package com.edushift.modules.payments.service;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.payments.dto.BulkCreateSubscriptionsRequest;
import com.edushift.modules.payments.dto.BulkCreateSubscriptionsResponse;
import com.edushift.modules.payments.dto.BillingPlanRuleRequest;
import com.edushift.modules.payments.dto.BillingPlanRuleResponse;
import com.edushift.modules.payments.dto.CreateSubscriptionRequest;
import com.edushift.modules.payments.dto.SubscriptionResponse;
import com.edushift.modules.payments.dto.UpdateBillingPlanRuleRequest;
import com.edushift.modules.payments.entity.BillingPlanRule;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.exception.PaymentConceptNotFoundException;
import com.edushift.modules.payments.job.InvoiceCronJob;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.modules.payments.repository.BillingPlanRuleRepository;
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
	private final BillingPlanRuleRepository billingPlanRuleRepository;
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
			BillingPlanRuleRepository billingPlanRuleRepository,
			SiblingDiscountService siblingDiscountService,
			@Lazy InvoiceCronJob invoiceCronJob) {
		this.subscriptionRepository = subscriptionRepository;
		this.studentRepository = studentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.conceptRepository = conceptRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.gradeRepository = gradeRepository;
		this.levelRepository = levelRepository;
		this.billingPlanRuleRepository = billingPlanRuleRepository;
		this.siblingDiscountService = siblingDiscountService;
		this.invoiceCronJob = invoiceCronJob;
	}

	@Transactional(readOnly = true)
	public List<BillingPlanRuleResponse> listBillingPlanRules() {
		TenantContext.currentRequired();
		return billingPlanRuleRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(this::ruleSnapshot)
				.toList();
	}

	@Transactional(readOnly = true)
	public BillingPlanRuleResponse previewBillingPlanRule(BillingPlanRuleRequest request) {
		TenantContext.currentRequired();
		return previewRule(toRule(request));
	}

	@Transactional
	public BillingPlanRuleResponse createBillingPlanRule(BillingPlanRuleRequest request) {
		TenantContext.currentRequired();
		BillingPlanRule rule = toRule(request);
		ensureNoActiveRule(rule);
		BillingPlanRule saved = billingPlanRuleRepository.saveAndFlush(rule);
		BillingPlanRuleResponse response = syncRule(saved);
		if (response.createdCount() > 0) siblingDiscountService.refreshSuggestions();
		return response;
	}

	@Transactional
	public BillingPlanRuleResponse updateBillingPlanRule(UUID publicUuid, UpdateBillingPlanRuleRequest request) {
		BillingPlanRule rule = billingPlanRuleRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new BusinessException("BILLING_PLAN_RULE_NOT_FOUND", "Automatic plan rule not found"));
		if (Boolean.TRUE.equals(request.active()) && !rule.isActive()) {
			ensureNoActiveRule(rule);
		}
		rule.setActive(request.active());
		BillingPlanRule saved = billingPlanRuleRepository.save(rule);
		if (saved.isActive()) return syncRule(saved);
		return ruleSnapshot(saved);
	}

	@Transactional
	public BillingPlanRuleResponse synchronizeBillingPlanRule(UUID publicUuid) {
		BillingPlanRule rule = billingPlanRuleRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new BusinessException("BILLING_PLAN_RULE_NOT_FOUND", "Automatic plan rule not found"));
		if (!rule.isActive()) {
			throw new BusinessException("BILLING_PLAN_RULE_INACTIVE", "Activate the rule before synchronizing it");
		}
		BillingPlanRuleResponse response = syncRule(rule);
		if (response.createdCount() > 0) siblingDiscountService.refreshSuggestions();
		return response;
	}

	@Transactional
	public void deleteBillingPlanRule(UUID publicUuid) {
		BillingPlanRule rule = billingPlanRuleRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new BusinessException("BILLING_PLAN_RULE_NOT_FOUND", "Automatic plan rule not found"));
		billingPlanRuleRepository.delete(rule);
	}

	/** Called after enrollment or guardian linkage; creates all matching plans idempotently. */
	@Transactional
	public int synchronizeStudentForBillingRules(UUID studentPublicUuid) {
		TenantContext.currentRequired();
		Student student = studentRepository.findByPublicUuidForUpdate(studentPublicUuid)
				.orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "Student not found"));
		List<StudentEnrollment> enrollments = enrollmentRepository.findActiveByStudentFetchSection(student);
		if (enrollments.isEmpty()) return 0;
		Set<UUID> activeGradeIds = new LinkedHashSet<>();
		Set<UUID> activeLevelIds = new LinkedHashSet<>();
		for (StudentEnrollment enrollment : enrollments) {
			Grade grade = enrollment.getSection().getGrade();
			activeGradeIds.add(grade.getId());
			activeLevelIds.add(grade.getLevel().getId());
		}
		int created = 0;
		for (BillingPlanRule rule : billingPlanRuleRepository.findByActiveTrueOrderByCreatedAtDesc()) {
			if ((rule.getGradeId() != null && activeGradeIds.contains(rule.getGradeId()))
					|| (rule.getLevelId() != null && activeLevelIds.contains(rule.getLevelId()))) {
				if (syncStudentForRule(rule, student) == SyncOutcome.CREATED) created++;
			}
		}
		if (created > 0) siblingDiscountService.refreshSuggestions();
		return created;
	}

	@Transactional
	public int synchronizeGuardianForBillingRules(UUID guardianInternalId) {
		TenantContext.currentRequired();
		Set<UUID> studentPublicUuids = new LinkedHashSet<>();
		for (StudentGuardian link : studentGuardianRepository.findActiveByGuardianId(guardianInternalId)) {
			if (link.getStudent() != null) studentPublicUuids.add(link.getStudent().getPublicUuid());
		}
		int created = 0;
		for (UUID studentPublicUuid : studentPublicUuids) {
			created += synchronizeStudentForBillingRules(studentPublicUuid);
		}
		return created;
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
		Student student = studentRepository.findByPublicUuidForUpdate(request.studentPublicUuid())
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
			student = studentRepository.findByPublicUuidForUpdate(student.getPublicUuid()).orElse(student);
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

	private BillingPlanRule toRule(BillingPlanRuleRequest request) {
		if ((request.levelPublicUuid() == null) == (request.gradePublicUuid() == null)) {
			throw new BusinessException("BILLING_PLAN_RULE_SCOPE_REQUIRED", "Provide exactly one level or grade");
		}
		PaymentConcept concept = requireConcept(request.conceptPublicUuid());
		if (!concept.isActive() || concept.getCategory() != PaymentConcept.Category.TUITION) {
			throw new BusinessException("BILLING_PLAN_RULE_TUITION_REQUIRED", "Automatic rules require an active tuition concept");
		}
		long amount = request.amountCents() == null ? concept.getDefaultAmountCents() : request.amountCents();
		if (amount <= 0) throw new BusinessException("INVALID_AMOUNT", "amountCents must be > 0");
		if (request.billingPeriod() == Subscription.BillingPeriod.ANNUAL
				&& (request.billingMonth() == null || request.billingMonth() < 1 || request.billingMonth() > 12)) {
			throw new BusinessException("INVALID_BILLING_MONTH", "billingMonth (1–12) is required for annual rules");
		}
		if (request.billingPeriod() == Subscription.BillingPeriod.MONTHLY && request.billingMonth() != null) {
			throw new BusinessException("INVALID_BILLING_MONTH", "billingMonth must be empty for monthly rules");
		}
		BillingPlanRule rule = new BillingPlanRule();
		rule.setTenantId(TenantContext.currentRequired());
		rule.setConceptId(concept.getId());
		rule.setAmountCents(amount);
		rule.setCurrency(request.currency() == null || request.currency().isBlank()
				? concept.getCurrency() : request.currency());
		rule.setBillingPeriod(request.billingPeriod());
		rule.setBillingDayOfMonth(request.billingDayOfMonth());
		rule.setBillingMonth(request.billingPeriod() == Subscription.BillingPeriod.ANNUAL
				? request.billingMonth() : null);
		if (request.gradePublicUuid() != null) {
			Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
					.orElseThrow(() -> new BusinessException("GRADE_NOT_FOUND", "Grade not found"));
			rule.setGradeId(grade.getId());
		} else {
			AcademicLevel level = levelRepository.findByPublicUuid(request.levelPublicUuid())
					.orElseThrow(() -> new BusinessException("LEVEL_NOT_FOUND", "Level not found"));
			rule.setLevelId(level.getId());
		}
		return rule;
	}

	private void ensureNoActiveRule(BillingPlanRule rule) {
		for (BillingPlanRule existing : billingPlanRuleRepository.findByActiveTrueOrderByCreatedAtDesc()) {
			if (!existing.getConceptId().equals(rule.getConceptId())) continue;
			boolean sameScope = rule.getGradeId() != null
					? rule.getGradeId().equals(existing.getGradeId())
					: rule.getLevelId().equals(existing.getLevelId());
			boolean overlaps = false;
			if (rule.getLevelId() != null && existing.getGradeId() != null) {
				Grade grade = gradeRepository.findById(existing.getGradeId()).orElse(null);
				overlaps = grade != null && grade.getLevel().getId().equals(rule.getLevelId());
			} else if (rule.getGradeId() != null && existing.getLevelId() != null) {
				Grade grade = gradeRepository.findById(rule.getGradeId()).orElse(null);
				overlaps = grade != null && grade.getLevel().getId().equals(existing.getLevelId());
			}
			if (sameScope || overlaps) {
				throw new BusinessException("BILLING_PLAN_RULE_SCOPE_OVERLAP",
						"An active rule for this tuition already covers the selected grade or level");
			}
		}
	}

	private BillingPlanRuleResponse syncRule(BillingPlanRule rule) {
		int created = 0;
		int alreadyActive = 0;
		int waitingGuardian = 0;
		List<Student> students = studentsInScope(rule);
		if (students.size() > BULK_MAX) {
			throw new BusinessException("BULK_LIMIT_EXCEEDED", "Automatic rule supports at most " + BULK_MAX + " students");
		}
		for (Student student : students) {
			SyncOutcome outcome = syncStudentForRule(rule, student);
			switch (outcome) {
				case CREATED -> created++;
				case ALREADY_ACTIVE -> alreadyActive++;
				case WAITING_GUARDIAN -> waitingGuardian++;
			}
		}
		return new BillingPlanRuleResponse(
				rule.getPublicUuid(), conceptPublicUuid(rule), levelPublicUuid(rule), gradePublicUuid(rule),
				rule.getAmountCents(), rule.getCurrency(), rule.getBillingPeriod(), rule.getBillingDayOfMonth(),
				rule.getBillingMonth(), rule.isActive(), students.size(), created, alreadyActive, waitingGuardian);
	}

	private BillingPlanRuleResponse previewRule(BillingPlanRule rule) {
		int wouldCreate = 0;
		int alreadyActive = 0;
		int waitingGuardian = 0;
		List<Student> students = studentsInScope(rule);
		for (Student student : students) {
			if (subscriptionRepository.existsByStudentIdAndConceptIdAndStatus(
					student.getPublicUuid(), rule.getConceptId(), Subscription.Status.ACTIVE)) {
				alreadyActive++;
			} else {
				try {
					resolveGuardianUserId(student);
					wouldCreate++;
				} catch (BusinessException ex) {
					waitingGuardian++;
				}
			}
		}
		return new BillingPlanRuleResponse(
				rule.getPublicUuid(), conceptPublicUuid(rule), levelPublicUuid(rule), gradePublicUuid(rule),
				rule.getAmountCents(), rule.getCurrency(), rule.getBillingPeriod(), rule.getBillingDayOfMonth(),
				rule.getBillingMonth(), rule.isActive(), students.size(), wouldCreate, alreadyActive, waitingGuardian);
	}

	private BillingPlanRuleResponse ruleSnapshot(BillingPlanRule rule) {
		return previewRule(rule);
	}

	private List<Student> studentsInScope(BillingPlanRule rule) {
		List<StudentEnrollment> enrollments;
		if (rule.getGradeId() != null) {
			Grade grade = gradeRepository.findById(rule.getGradeId())
					.orElseThrow(() -> new BusinessException("GRADE_NOT_FOUND", "Grade not found"));
			enrollments = enrollmentRepository.findActiveByGrade(grade);
		} else {
			AcademicLevel level = levelRepository.findById(rule.getLevelId())
					.orElseThrow(() -> new BusinessException("LEVEL_NOT_FOUND", "Level not found"));
			enrollments = enrollmentRepository.findActiveByLevel(level);
		}
		Set<UUID> seen = new LinkedHashSet<>();
		List<Student> students = new ArrayList<>();
		for (StudentEnrollment enrollment : enrollments) {
			Student student = enrollment.getStudent();
			if (student != null && seen.add(student.getId())) students.add(student);
		}
		return students;
	}

	private SyncOutcome syncStudentForRule(BillingPlanRule rule, Student student) {
		student = studentRepository.findByPublicUuidForUpdate(student.getPublicUuid()).orElse(student);
		if (subscriptionRepository.existsByStudentIdAndConceptIdAndStatus(
				student.getPublicUuid(), rule.getConceptId(), Subscription.Status.ACTIVE)) {
			return SyncOutcome.ALREADY_ACTIVE;
		}
		UUID guardianUserId;
		try {
			guardianUserId = resolveGuardianUserId(student);
		} catch (BusinessException ex) {
			return SyncOutcome.WAITING_GUARDIAN;
		}
		PaymentConcept concept = conceptRepository.findById(rule.getConceptId())
				.orElseThrow(() -> new PaymentConceptNotFoundException("Payment concept not found"));
		Subscription subscription = buildSubscription(
				student, guardianUserId, concept, null, rule.getAmountCents(), rule.getBillingPeriod(),
				rule.getBillingDayOfMonth(), rule.getBillingMonth(), rule.getCurrency());
		subscriptionRepository.save(subscription);
		return SyncOutcome.CREATED;
	}

	private UUID conceptPublicUuid(BillingPlanRule rule) {
		return conceptRepository.findById(rule.getConceptId())
				.orElseThrow(() -> new PaymentConceptNotFoundException("Payment concept not found"))
				.getPublicUuid();
	}

	private UUID levelPublicUuid(BillingPlanRule rule) {
		if (rule.getLevelId() == null) return null;
		return levelRepository.findById(rule.getLevelId())
				.orElseThrow(() -> new BusinessException("LEVEL_NOT_FOUND", "Level not found"))
				.getPublicUuid();
	}

	private UUID gradePublicUuid(BillingPlanRule rule) {
		if (rule.getGradeId() == null) return null;
		return gradeRepository.findById(rule.getGradeId())
				.orElseThrow(() -> new BusinessException("GRADE_NOT_FOUND", "Grade not found"))
				.getPublicUuid();
	}

	private enum SyncOutcome { CREATED, ALREADY_ACTIVE, WAITING_GUARDIAN }

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
