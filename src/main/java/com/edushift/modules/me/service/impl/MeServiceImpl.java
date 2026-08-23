package com.edushift.modules.me.service.impl;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.me.dto.MeProfileResponse;
import com.edushift.modules.me.service.MeService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link MeService} implementation (DEBT-STUDENT-PRIVACY / Fase 1).
 *
 * <p>Resolves the caller via {@link CurrentUserProvider}, loads the linked
 * {@code Student} row (via {@link StudentRepository#findByUserId}), the
 * active {@link StudentEnrollment} (to populate section / grade / academic
 * year), the {@link Tenant} (to populate branding) and the caller's
 * granted authorities (read from the {@link Authentication} — these are
 * the {@code LMS_*} and {@code ME_*} authorities minted at JWT time).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeServiceImpl implements MeService {

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public MeProfileResponse getProfile() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_AUTHENTICATED",
                        "Authenticated user is required to read the profile"));
        UUID studentPublicUuid = currentUserProvider.currentStudentPublicUuid()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_A_STUDENT",
                        "Caller has no student record in this tenant"));
        Student student = studentRepository.findByPublicUuid(studentPublicUuid)
                .orElseThrow(() -> new NotFoundException(
                        "ME_STUDENT_NOT_FOUND",
                        "Linked student row was not found in the current tenant"));

        UUID tenantId = currentUserProvider.currentTenantId()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NO_TENANT",
                        "Authenticated tenant is required to read the profile"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException(
                        "ME_TENANT_NOT_FOUND",
                        "Tenant not found for the authenticated user"));

        String email = userRepository.findById(student.getUserId())
                .map(u -> u.getEmail())
                .orElse(null);

        // Active enrollment — pick the newest ACTIVE row. If the student
        // happens to be without an active enrollment (summer break,
        // administrative withdrawal), fall back to "no section" and let
        // the FE render an "out of school year" banner.
        StudentEnrollment active = enrollmentRepository
                .findActiveByStudentFetchSection(student)
                .stream()
                .findFirst()
                .orElse(null);

        UUID sectionPublicUuid = active != null && active.getSection() != null
                ? active.getSection().getPublicUuid() : null;
        String sectionName = active != null && active.getSection() != null
                ? active.getSection().getName() : null;
        String gradeName = active != null && active.getSection() != null
                && active.getSection().getGrade() != null
                ? active.getSection().getGrade().getName() : null;
        String academicYearLabel = active != null && active.getAcademicYear() != null
                ? active.getAcademicYear().getName() : null;

        String tenantLogoUrl = tenant.getBranding() != null
                && tenant.getBranding().get("logoUrl") instanceof String s
                ? s : null;

        List<String> permissions = authenticationAuthorities();

        return new MeProfileResponse(
                student.getPublicUuid(),
                student.getUserId(),
                student.getFirstName(),
                student.getLastName(),
                email,
                sectionPublicUuid,
                sectionName,
                gradeName,
                academicYearLabel,
                tenant.getPublicUuid(),
                tenant.getName(),
                tenantLogoUrl,
                permissions);
    }

    /**
     * Returns the caller's granted authorities as strings, filtered to
     * {@code ME_*} / {@code LMS_*} (the FE doesn't need ROLE_*
     * authorities for the STUDENT shell). Order is the SecurityContext's
     * own iteration order, which is stable for the request lifetime.
     */
    private List<String> authenticationAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null
                        && (a.startsWith("ME_") || a.startsWith("LMS_")))
                .distinct()
                .collect(Collectors.toList());
    }
}
