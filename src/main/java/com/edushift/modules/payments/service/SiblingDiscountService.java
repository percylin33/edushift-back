package com.edushift.modules.payments.service;

import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.payments.dto.SiblingSuggestionGroupResponse;
import com.edushift.modules.payments.dto.SiblingSuggestionGroupResponse.SiblingSuggestionChildResponse;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.repository.SubscriptionRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects sibling groups (same guardian, 2+ actively enrolled students) and
 * exposes them as staff suggestions. Never applies money automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiblingDiscountService {

	private final PaymentPolicyService paymentPolicyService;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final SubscriptionRepository subscriptionRepository;

	@Transactional(readOnly = true)
	public List<SiblingSuggestionGroupResponse> listSuggestions() {
		TenantContext.currentRequired();
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		if (!policy.siblingDiscountEnabled() || policy.siblingDiscountCents() <= 0) {
			return List.of();
		}
		return buildGroups(policy, false);
	}

	/**
	 * Marks ACTIVE subscriptions of sibling candidates as {@code SUGGESTED}
	 * without overwriting {@code APPLIED}/{@code REJECTED}.
	 *
	 * @return number of subscriptions newly marked SUGGESTED
	 */
	@Transactional
	public int refreshSuggestions() {
		TenantContext.currentRequired();
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		if (!policy.siblingDiscountEnabled() || policy.siblingDiscountCents() <= 0) {
			return 0;
		}
		List<SiblingSuggestionGroupResponse> groups = buildGroups(policy, true);
		int marked = 0;
		for (SiblingSuggestionGroupResponse g : groups) {
			for (SiblingSuggestionChildResponse child : g.children()) {
				if (child.siblingDiscountStatus() == Subscription.SiblingDiscountStatus.NONE
						|| child.siblingDiscountStatus() == Subscription.SiblingDiscountStatus.SUGGESTED) {
					List<Subscription> subs = subscriptionRepository.findByStudentIdAndStatus(
							child.studentPublicUuid(), Subscription.Status.ACTIVE);
					for (Subscription s : subs) {
						if (s.getSiblingDiscountStatus() == Subscription.SiblingDiscountStatus.NONE) {
							s.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.SUGGESTED);
							subscriptionRepository.save(s);
							marked++;
						}
					}
				}
			}
		}
		log.info("[sibling-discount] refresh marked {} subscriptions SUGGESTED", marked);
		return marked;
	}

	@Transactional
	public int decide(List<UUID> studentPublicUuids, Subscription.SiblingDiscountStatus decision) {
		TenantContext.currentRequired();
		if (decision != Subscription.SiblingDiscountStatus.APPLIED
				&& decision != Subscription.SiblingDiscountStatus.REJECTED) {
			throw new BusinessException(
					"INVALID_SIBLING_DECISION",
					"decision must be APPLIED or REJECTED");
		}
		if (studentPublicUuids == null || studentPublicUuids.isEmpty()) {
			return 0;
		}
		List<Subscription> subs = subscriptionRepository.findByStudentIdInAndStatus(
				studentPublicUuids, Subscription.Status.ACTIVE);
		int updated = 0;
		for (Subscription s : subs) {
			s.setSiblingDiscountStatus(decision);
			subscriptionRepository.save(s);
			updated++;
		}
		log.info("[sibling-discount] decided {} for {} subscriptions", decision, updated);
		return updated;
	}

	/**
	 * Discount cents to apply when issuing an invoice for this subscription.
	 * Returns 0 unless staff accepted ({@code APPLIED}) and policy is enabled.
	 */
	@Transactional(readOnly = true)
	public long resolvedDiscountCents(Subscription subscription) {
		if (subscription == null) {
			return 0L;
		}
		if (subscription.getSiblingDiscountStatus() != Subscription.SiblingDiscountStatus.APPLIED) {
			return 0L;
		}
		PaymentPolicyDto policy = paymentPolicyService.getForTenant(subscription.getTenantId());
		if (!policy.siblingDiscountEnabled() || policy.siblingDiscountCents() <= 0) {
			return 0L;
		}
		return Math.min(policy.siblingDiscountCents(), subscription.getAmountCents());
	}

	private List<SiblingSuggestionGroupResponse> buildGroups(PaymentPolicyDto policy, boolean forRefresh) {
		Map<UUID, Student> enrolledByInternalId = new HashMap<>();
		for (StudentEnrollment e : enrollmentRepository.findAll()) {
			if (e.getStatus() != StudentEnrollmentStatus.ACTIVE || e.getStudent() == null) {
				continue;
			}
			enrolledByInternalId.put(e.getStudent().getId(), e.getStudent());
		}

		List<StudentGuardian> links = studentGuardianRepository.findAllActiveWithStudentAndGuardian();
		Map<UUID, List<StudentGuardian>> byGuardian = new LinkedHashMap<>();
		for (StudentGuardian link : links) {
			if (link.getStudent() == null || link.getGuardian() == null) {
				continue;
			}
			if (!enrolledByInternalId.containsKey(link.getStudent().getId())) {
				continue;
			}
			byGuardian.computeIfAbsent(link.getGuardian().getId(), k -> new ArrayList<>()).add(link);
		}

		int minCount = policy.siblingMinCount();
		List<SiblingSuggestionGroupResponse> out = new ArrayList<>();
		for (Map.Entry<UUID, List<StudentGuardian>> entry : byGuardian.entrySet()) {
			Map<UUID, StudentGuardian> uniqueStudents = new LinkedHashMap<>();
			for (StudentGuardian sg : entry.getValue()) {
				uniqueStudents.putIfAbsent(sg.getStudent().getId(), sg);
			}
			if (uniqueStudents.size() < minCount) {
				continue;
			}

			Guardian guardian = uniqueStudents.values().iterator().next().getGuardian();
			List<SiblingSuggestionChildResponse> children = new ArrayList<>();
			for (StudentGuardian sg : uniqueStudents.values()) {
				Student student = sg.getStudent();
				List<Subscription> activePlans = subscriptionRepository.findByStudentIdAndStatus(
						student.getPublicUuid(), Subscription.Status.ACTIVE);
				if (activePlans.isEmpty()) {
					continue;
				}
				for (Subscription sub : activePlans) {
					children.add(new SiblingSuggestionChildResponse(
							student.getPublicUuid(),
							student.fullName(),
							sub.getPublicUuid(),
							sub.getPlanCode(),
							sub.getAmountCents(),
							sub.getCurrency(),
							sub.getSiblingDiscountStatus() == null
									? Subscription.SiblingDiscountStatus.NONE
									: sub.getSiblingDiscountStatus()));
				}
			}
			if (children.isEmpty()) {
				continue;
			}
			String guardianName = ((guardian.getFirstName() == null ? "" : guardian.getFirstName()) + " "
					+ (guardian.getLastName() == null ? "" : guardian.getLastName())).trim();
			String doc = (guardian.getDocumentType() == null ? "" : guardian.getDocumentType() + " ")
					+ (guardian.getDocumentNumber() == null ? "" : guardian.getDocumentNumber());
			out.add(new SiblingSuggestionGroupResponse(
					guardian.getId(),
					guardianName,
					doc.trim(),
					uniqueStudents.size(),
					policy.siblingDiscountCents(),
					children));
		}
		return out;
	}
}
