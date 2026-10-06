package com.edushift.modules.payments.service;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.payments.dto.BulkCreateSubscriptionsRequest;
import com.edushift.modules.payments.dto.BulkCreateSubscriptionsResponse;
import com.edushift.modules.payments.dto.CreateSubscriptionRequest;
import com.edushift.modules.payments.dto.SubscriptionResponse;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.exception.PaymentConceptNotFoundException;
import com.edushift.modules.payments.job.InvoiceCronJob;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.modules.payments.repository.SubscriptionRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SubscriptionService {

	private static final int BULK_MAX = 500;

	private final SubscriptionRepository subscriptionRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final PaymentConceptRepository conceptRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final GradeRepository gradeRepository;
	private final AcademicLevelRepository levelRepository;
	private final SiblingDiscountService siblingDiscountService;
	private final InvoiceCronJob invoiceCronJob;

	public SubscriptionService(
			SubscriptionRepository subscriptionRepository,
			StudentRepository studentRepository,
			StudentGuardianRepository studentGuardianRepository,
			PaymentConceptRepository conceptRepository,
			StudentEnrollmentRepository enrollmentRepository,
			GradeRepository gradeRepository,
			AcademicLevelRepository levelRepository,
			SiblingDiscountService siblingDiscountService,
			@Lazy InvoiceCronJob invoiceCronJob) {
		this.subscriptionRepository = subscriptionRepository;
		this.studentRepository = studentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.conceptRepository = conceptRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.gradeRepository = gradeRepository;
		this.levelRepository = levelRepository;
		this.siblingDiscountService = siblingDiscountService;
		this.invoiceCronJob = invoiceCronJob;
	}

	@Transactional(readOnly = true)
	public Page<SubscriptionResponse> list(Subscription.Status status, Pageable pageable) {
		TenantContext.currentRequired();
		Page<Subscription> page = status == null
				? subscriptionRepository.findAllByOrderByCreatedAtDesc(pageable)
				: subscriptionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
		return page.map(SubscriptionResponse::from);
	}

	@Transactional
	public SubscriptionResponse create(CreateSubscriptionRequest request) {
		TenantContext.currentRequired();
		Student student = studentRepository.findByPublicUuid(request.studentPublicUuid())
				.orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "Student not found"));
		PaymentConcept concept = requireConcept(request.conceptPublicUuid());
		if (subscriptionRepository.existsByStudentIdAndConceptIdAndStatus(
				student.getPublicUuid(), concept.getId(), Subscription.Status.ACTIVE)) {
			throw new BusinessException(
					"SUBSCRIPTION_ALREADY_ACTIVE",
					"Student already has an active plan for this concept");
		}
		UUID guardianUserId = resolveGuardianUserId(student);
		Subscription s = buildSubscription(
				student,
				guardianUserId,
				concept,
				request.planCode(),
				request.amountCents(),
				request.billingPeriod(),
				request.billingDayOfMonth(),
				request.billingMonth(),
				request.currency());
		s = subscriptionRepository.save(s);
		siblingDiscountService.refreshSuggestions();
		log.info("[subscription] created {} for student={}", s.getPublicUuid(), student.getPublicUuid());
		return SubscriptionResponse.from(s);
	}

	@Transactional
	public BulkCreateSubscriptionsResponse bulkCreate(BulkCreateSubscriptionsRequest request) {
		TenantContext.currentRequired();
		if (request.levelPublicUuid() == null && request.gradePublicUuid() == null) {
			throw new BusinessException(
					"BULK_SCOPE_REQUIRED",
					"Provide levelPublicUuid or gradePublicUuid");
		}
		if (request.levelPublicUuid() != null && request.gradePublicUuid() != null) {
			throw new BusinessException(
					"BULK_SCOPE_AMBIGUOUS",
					"Provide only one of levelPublicUuid or gradePublicUuid");
		}
		PaymentConcept concept = requireConcept(request.conceptPublicUuid());
		List<Student> students = resolveBulkStudents(request);
		if (students.size() > BULK_MAX) {
			throw new BusinessException(
					"BULK_LIMIT_EXCEEDED",
					"Bulk assign supports at most " + BULK_MAX + " students (got " + students.size() + ")");
		}

		int skippedAlready = 0;
		int skippedNoGuardian = 0;
		List<UUID> created = new ArrayList<>();
		for (Student student : students) {
			if (subscriptionRepository.existsByStudentIdAndConceptIdAndStatus(
					student.getPublicUuid(), concept.getId(), Subscription.Status.ACTIVE)) {
				skippedAlready++;
				continue;
			}
			UUID guardianUserId;
			try {
				guardianUserId = resolveGuardianUserId(student);
			} catch (BusinessException ex) {
				skippedNoGuardian++;
				continue;
			}
			if (request.dryRun()) {
				created.add(student.getPublicUuid());
				continue;
			}
			Subscription s = buildSubscription(
					student,
					guardianUserId,
					concept,
					request.planCode(),
					request.amountCents(),
					request.billingPeriod(),
					request.billingDayOfMonth(),
					request.billingMonth(),
					request.currency());
			s = subscriptionRepository.save(s);
			created.add(s.getPublicUuid());
		}

		int suggestions = 0;
		if (!request.dryRun() && !created.isEmpty()) {
			suggestions = siblingDiscountService.refreshSuggestions();
		}

		return new BulkCreateSubscriptionsResponse(
				request.dryRun(),
				students.size(),
				request.dryRun() ? 0 : created.size(),
				skippedAlready,
				skippedNoGuardian,
				suggestions,
				request.dryRun() ? List.of() : List.copyOf(created));
	}

	@Transactional
	public SubscriptionResponse pause(UUID publicUuid) {
		Subscription s = mustFind(publicUuid);
		if (s.getStatus() == Subscription.Status.CANCELLED) {
			throw new BusinessException("SUBSCRIPTION_CANCELLED", "Cancelled plans cannot be paused");
		}
		s.setStatus(Subscription.Status.PAUSED);
		return SubscriptionResponse.from(subscriptionRepository.save(s));
	}

	@Transactional
	public SubscriptionResponse resume(UUID publicUuid) {
		Subscription s = mustFind(publicUuid);
		if (s.getStatus() == Subscription.Status.CANCELLED) {
			throw new BusinessException("SUBSCRIPTION_CANCELLED", "Cancelled plans cannot be resumed");
		}
		s.setStatus(Subscription.Status.ACTIVE);
		if (s.getNextBillingAt() == null) {
			s.setNextBillingAt(Instant.now());
		}
		return SubscriptionResponse.from(subscriptionRepository.save(s));
	}

	@Transactional
	public SubscriptionResponse cancel(UUID publicUuid) {
		Subscription s = mustFind(publicUuid);
		s.setStatus(Subscription.Status.CANCELLED);
		s.setCancelledAt(Instant.now());
		s.setNextBillingAt(null);
		return SubscriptionResponse.from(subscriptionRepository.save(s));
	}

	/**
	 * UAT helper: issue due invoices now (same logic as nightly cron).
	 */
	@Transactional
	public int runDueNow() {
		TenantContext.currentRequired();
		Instant now = Instant.now();
		List<Subscription> due = subscriptionRepository.findDueForBilling(
				now, org.springframework.data.domain.PageRequest.of(0, 200));
		int issued = 0;
		for (Subscription s : due) {
			if (!TenantContext.currentRequired().equals(s.getTenantId())) {
				continue;
			}
			try {
				invoiceCronJob.issueOne(s, now);
				issued++;
			} catch (Exception ex) {
				log.warn("[subscription] run-due failed for {}: {}", s.getPublicUuid(), ex.getMessage());
			}
		}
		return issued;
	}

	private Subscription buildSubscription(
			Student student,
			UUID guardianUserId,
			PaymentConcept concept,
			String planCode,
			Long amountCents,
			Subscription.BillingPeriod billingPeriod,
			Integer billingDayOfMonth,
			Integer billingMonth,
			String currency) {
		long amount = amountCents != null && amountCents > 0
				? amountCents
				: concept.getDefaultAmountCents();
		if (amount <= 0) {
			throw new BusinessException("INVALID_AMOUNT", "amountCents must be > 0");
		}
		if (billingDayOfMonth == null || billingDayOfMonth < 1 || billingDayOfMonth > 31) {
			throw new BusinessException("INVALID_BILLING_DAY", "billingDayOfMonth must be 1–31");
		}
		if (billingPeriod == Subscription.BillingPeriod.ANNUAL
				&& (billingMonth == null || billingMonth < 1 || billingMonth > 12)) {
			throw new BusinessException(
					"INVALID_BILLING_MONTH",
					"billingMonth (1–12) is required for annual plans");
		}
		String code = planCode != null && !planCode.isBlank()
				? planCode.trim()
				: concept.getCode();
		ZoneId zone = ZoneId.systemDefault();
		LocalDate today = LocalDate.now(zone);
		int day = BillingSchedule.clampDay(billingDayOfMonth);
		Integer month = billingPeriod == Subscription.BillingPeriod.ANNUAL
				? BillingSchedule.clampMonth(billingMonth)
				: null;
		LocalDate nextDate = billingPeriod == Subscription.BillingPeriod.ANNUAL
				? BillingSchedule.nextAnnualOnOrAfter(today, month, day)
				: BillingSchedule.nextMonthlyOnOrAfter(today, day);

		Subscription s = new Subscription();
		s.setTenantId(TenantContext.currentRequired());
		s.setStudentId(student.getPublicUuid());
		s.setGuardianUserId(guardianUserId);
		s.setConceptId(concept.getId());
		s.setPlanCode(code);
		s.setAmountCents(amount);
		s.setCurrency(currency == null || currency.isBlank()
				? (concept.getCurrency() == null ? "PEN" : concept.getCurrency())
				: currency);
		s.setBillingPeriod(billingPeriod);
		s.setBillingDayOfMonth(day);
		s.setBillingMonth(month);
		s.setStatus(Subscription.Status.ACTIVE);
		s.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.NONE);
		s.setStartAt(Instant.now());
		s.setNextBillingAt(BillingSchedule.toStartOfDay(nextDate, zone));
		return s;
	}

	private List<Student> resolveBulkStudents(BulkCreateSubscriptionsRequest request) {
		Set<UUID> seen = new LinkedHashSet<>();
		List<Student> out = new ArrayList<>();
		List<StudentEnrollment> enrollments;
		if (request.gradePublicUuid() != null) {
			Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
					.orElseThrow(() -> new BusinessException("GRADE_NOT_FOUND", "Grade not found"));
			enrollments = enrollmentRepository.findActiveByGrade(grade);
		} else {
			AcademicLevel level = levelRepository.findByPublicUuid(request.levelPublicUuid())
					.orElseThrow(() -> new BusinessException("LEVEL_NOT_FOUND", "Level not found"));
			enrollments = enrollmentRepository.findActiveByLevel(level);
		}
		for (StudentEnrollment e : enrollments) {
			Student s = e.getStudent();
			if (s != null && seen.add(s.getId())) {
				out.add(s);
			}
		}
		return out;
	}

	private PaymentConcept requireConcept(UUID conceptPublicUuid) {
		return conceptRepository.findByPublicUuidAndDeletedFalse(conceptPublicUuid)
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + conceptPublicUuid));
	}

	private Subscription mustFind(UUID publicUuid) {
		return subscriptionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new BusinessException("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
	}

	private UUID resolveGuardianUserId(Student student) {
		List<StudentGuardian> links = studentGuardianRepository.findActiveByStudentId(student.getId());
		StudentGuardian primary = links.stream()
				.filter(StudentGuardian::isPrimaryContact)
				.filter(sg -> sg.getGuardian() != null && sg.getGuardian().getUserId() != null)
				.findFirst()
				.orElseGet(() -> links.stream()
						.filter(sg -> sg.getGuardian() != null && sg.getGuardian().getUserId() != null)
						.findFirst()
						.orElse(null));
		if (primary == null) {
			throw new BusinessException(
					"GUARDIAN_NOT_LINKED",
					"Student has no guardian with a portal user account");
		}
		return primary.getGuardian().getUserId();
	}
}
