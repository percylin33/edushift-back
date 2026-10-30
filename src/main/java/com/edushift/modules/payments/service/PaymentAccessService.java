package com.edushift.modules.payments.service;

import com.edushift.modules.payments.dto.PaymentAccessCandidateDto;
import com.edushift.modules.payments.dto.PaymentAccessOverrideDto;
import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.PaymentAccessOverride;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentAccessOverrideRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft LMS gate for delinquency. Never blocks login or Payments routes —
 * callers must only invoke this from LMS mutation endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAccessService {

	private final PaymentPolicyService paymentPolicyService;
	private final InvoiceRepository invoiceRepository;
	private final PaymentAccessOverrideRepository overrideRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public boolean isLmsRestricted(UUID userPublicUuid) {
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		if (!policy.lockEnabled()) {
			return false;
		}
		Instant now = Instant.now();
		if (!overrideRepository.findActiveForUser(userPublicUuid, now).isEmpty()) {
			return false;
		}
		Set<UUID> studentPublicUuids = resolveLinkedStudentPublicUuids(userPublicUuid);
		if (studentPublicUuids.isEmpty()) {
			return false;
		}
		Instant threshold = now.minus(Math.max(0, policy.lockOverdueDays()), ChronoUnit.DAYS);
		for (UUID studentPublicUuid : studentPublicUuids) {
			List<Invoice> overdue = invoiceRepository.findOverdueForStudent(studentPublicUuid);
			boolean hits = overdue.stream().anyMatch(inv ->
					inv.getDueAt() != null && inv.getDueAt().isBefore(threshold));
			if (hits) {
				return true;
			}
		}
		return false;
	}

	@Transactional(readOnly = true)
	public void assertLmsAllowed(UUID userPublicUuid) {
		if (isLmsRestricted(userPublicUuid)) {
			throw new BusinessException(
					"PAYMENT_LMS_RESTRICTED",
					"Acceso LMS limitado por cuota vencida. Regulariza el pago en Pagos.");
		}
	}

	@Transactional
	public PaymentAccessOverride createOverride(
			UUID userPublicUuid, UUID studentPublicUuid, Instant untilAt, String reason) {
		TenantContext.currentRequired();
		if (untilAt == null || !untilAt.isAfter(Instant.now())) {
			throw new BusinessException("INVALID_UNTIL", "untilAt must be in the future");
		}
		User user = userRepository.findByPublicUuid(userPublicUuid)
				.orElseThrow(() -> new BusinessException("PAYMENT_OVERRIDE_USER_NOT_FOUND", "Cuenta del estudiante o apoderado no encontrada"));
		if (!user.hasRole(UserRole.STUDENT) && !user.hasRole(UserRole.PARENT)) {
			throw new BusinessException("PAYMENT_OVERRIDE_ROLE_INVALID", "La excepción solo aplica a estudiantes o apoderados");
		}
		if (studentPublicUuid != null) {
			Student student = studentRepository.findByPublicUuid(studentPublicUuid)
					.orElseThrow(() -> new BusinessException("PAYMENT_OVERRIDE_STUDENT_NOT_FOUND", "Estudiante no encontrado en este colegio"));
			boolean isStudentAccount = userPublicUuid.equals(student.getUserId());
			boolean isLinkedGuardian = studentGuardianRepository.existsActiveLinkForParent(student.getId(), userPublicUuid);
			if (!isStudentAccount && !isLinkedGuardian) {
				throw new BusinessException("PAYMENT_OVERRIDE_LINK_MISMATCH", "La cuenta no está vinculada a este estudiante");
			}
		}
		PaymentAccessOverride o = new PaymentAccessOverride();
		o.setTenantId(TenantContext.currentRequired());
		o.setUserPublicUuid(userPublicUuid);
		o.setStudentPublicUuid(studentPublicUuid);
		o.setUntilAt(untilAt);
		o.setReason(reason);
		return overrideRepository.save(o);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccessCandidateDto> searchOverrideCandidates(String search) {
		TenantContext.currentRequired();
		String term = search == null ? "" : search.trim();
		if (term.length() < 2) {
			return List.of();
		}

		List<Student> students = studentRepository.searchForPaymentAccessOverride(term, PageRequest.of(0, 20));
		if (students.isEmpty()) {
			return List.of();
		}

		Map<String, PaymentAccessCandidateDto> candidates = new LinkedHashMap<>();
		for (Student student : students) {
			if (student.getUserId() != null) {
				addCandidate(candidates, student.getUserId(), student.getPublicUuid(), studentName(student), "Estudiante");
			}
		}

		List<UUID> studentIds = students.stream().map(Student::getId).toList();
		for (StudentGuardian link : studentGuardianRepository.findActiveByStudentIdsWithLinkedGuardian(studentIds)) {
			Student student = link.getStudent();
			var guardian = link.getGuardian();
			if (guardian.getUserId() != null) {
				String displayName = guardianName(guardian) + " · apoderado de " + studentName(student);
				addCandidate(candidates, guardian.getUserId(), student.getPublicUuid(), displayName, "Apoderado");
			}
		}
		for (StudentGuardian link : studentGuardianRepository.searchLinkedGuardiansForPaymentOverride(
				term, PageRequest.of(0, 20))) {
			var student = link.getStudent();
			var guardian = link.getGuardian();
			String displayName = guardianName(guardian) + " · apoderado de " + studentName(student);
			addCandidate(candidates, guardian.getUserId(), student.getPublicUuid(), displayName, "Apoderado");
		}
		return List.copyOf(candidates.values());
	}

	@Transactional(readOnly = true)
	public List<PaymentAccessOverrideDto> listActiveOverrides() {
		UUID tenantId = TenantContext.currentRequired();
		List<PaymentAccessOverride> overrides = overrideRepository.findActiveByTenant(tenantId, Instant.now());
		if (overrides.isEmpty()) {
			return List.of();
		}
		List<UUID> userIds = overrides.stream().map(PaymentAccessOverride::getUserPublicUuid).distinct().toList();
		List<UUID> studentIds = overrides.stream().map(PaymentAccessOverride::getStudentPublicUuid)
				.filter(java.util.Objects::nonNull).distinct().toList();
		Map<UUID, User> users = userRepository.findAllByPublicUuidIn(userIds).stream()
				.collect(java.util.stream.Collectors.toMap(User::getPublicUuid, user -> user));
		Map<UUID, Student> students = studentIds.isEmpty() ? Map.of() :
				studentRepository.findAllByPublicUuidIn(studentIds).stream()
						.collect(java.util.stream.Collectors.toMap(Student::getPublicUuid, student -> student));
		return overrides.stream().map(override -> {
			User user = users.get(override.getUserPublicUuid());
			Student student = students.get(override.getStudentPublicUuid());
			return new PaymentAccessOverrideDto(
					override.getPublicUuid(),
					override.getUserPublicUuid(),
					user == null ? "Usuario " + shortId(override.getUserPublicUuid()) : userName(user),
					override.getStudentPublicUuid(),
					student == null ? null : studentName(student),
					override.getReason(),
					override.getUntilAt());
		}).toList();
	}

	@Transactional
	public void revokeOverride(UUID publicUuid) {
		UUID tenantId = TenantContext.currentRequired();
		PaymentAccessOverride override = overrideRepository.findByPublicUuidAndTenantId(publicUuid, tenantId)
				.orElseThrow(() -> new BusinessException("PAYMENT_OVERRIDE_NOT_FOUND", "Excepción de acceso no encontrada"));
		override.setUntilAt(Instant.now());
		overrideRepository.save(override);
	}

	private static void addCandidate(
			Map<String, PaymentAccessCandidateDto> candidates,
			UUID userUuid,
			UUID studentUuid,
			String displayName,
			String relationship) {
		String key = userUuid + ":" + studentUuid;
		candidates.putIfAbsent(key, new PaymentAccessCandidateDto(userUuid, studentUuid, displayName, relationship));
	}

	private static String studentName(Student student) {
		return String.join(" ", java.util.stream.Stream.of(
				student.getFirstName(), student.getLastName(), student.getSecondLastName())
				.filter(value -> value != null && !value.isBlank()).toList());
	}

	private static String guardianName(com.edushift.modules.students.entity.Guardian guardian) {
		return String.join(" ", java.util.stream.Stream.of(guardian.getFirstName(), guardian.getLastName())
				.filter(value -> value != null && !value.isBlank()).toList());
	}

	private static String userName(User user) {
		return String.join(" ", java.util.stream.Stream.of(user.getFirstName(), user.getLastName())
				.filter(value -> value != null && !value.isBlank()).toList());
	}

	private static String shortId(UUID uuid) {
		return uuid.toString().substring(0, 8);
	}

	private Set<UUID> resolveLinkedStudentPublicUuids(UUID userPublicUuid) {
		Set<UUID> ids = new HashSet<>();
		studentRepository.findByUserId(userPublicUuid)
				.ifPresent(s -> ids.add(s.getPublicUuid()));
		studentGuardianRepository.findActiveByGuardianUserId(userPublicUuid)
				.forEach(sg -> {
					if (sg.getStudent() != null) {
						ids.add(sg.getStudent().getPublicUuid());
					}
				});
		return ids;
	}
}
