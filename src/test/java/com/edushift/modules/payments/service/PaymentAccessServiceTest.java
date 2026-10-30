package com.edushift.modules.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.payments.entity.PaymentAccessOverride;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentAccessOverrideRepository;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PaymentAccessServiceTest {

    private final PaymentPolicyService policyService = mock(PaymentPolicyService.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final PaymentAccessOverrideRepository overrideRepository = mock(PaymentAccessOverrideRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentGuardianRepository studentGuardianRepository = mock(StudentGuardianRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentAccessService service = new PaymentAccessService(
            policyService, invoiceRepository, overrideRepository,
            studentRepository, studentGuardianRepository, userRepository);
    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void searchOverrideCandidatesReturnsLinkedStudentAndGuardianAccounts() {
        TenantContext.set(tenantId);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setPublicUuid(UUID.randomUUID());
        student.setFirstName("Ana");
        student.setLastName("Ruiz");
        student.setUserId(UUID.randomUUID());

        Guardian guardian = new Guardian();
        guardian.setFirstName("Luis");
        guardian.setLastName("Ruiz");
        guardian.setUserId(UUID.randomUUID());

        StudentGuardian link = new StudentGuardian();
        link.setStudent(student);
        link.setGuardian(guardian);
        when(studentRepository.searchForPaymentAccessOverride(eq("ana"), any())).thenReturn(List.of(student));
        when(studentGuardianRepository.findActiveByStudentIdsWithLinkedGuardian(List.of(student.getId())))
                .thenReturn(List.of(link));
        when(studentGuardianRepository.searchLinkedGuardiansForPaymentOverride(eq("ana"), any()))
            .thenReturn(List.of(link));

        var candidates = service.searchOverrideCandidates("  ana  ");

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting("relationship").containsExactly("Estudiante", "Apoderado");
        assertThat(candidates).extracting("studentPublicUuid").containsOnly(student.getPublicUuid());
        verify(studentRepository).searchForPaymentAccessOverride(eq("ana"), any());
    }

    @Test
    void revokeOverrideLoadsAndPersistsOnlyWithinCurrentTenant() {
        TenantContext.set(tenantId);
        UUID publicUuid = UUID.randomUUID();
        PaymentAccessOverride override = new PaymentAccessOverride();
        override.setPublicUuid(publicUuid);
        override.setUntilAt(Instant.now().plusSeconds(3600));
        when(overrideRepository.findByPublicUuidAndTenantId(publicUuid, tenantId))
                .thenReturn(Optional.of(override));

        service.revokeOverride(publicUuid);

        assertThat(override.getUntilAt()).isBeforeOrEqualTo(Instant.now());
        verify(overrideRepository).findByPublicUuidAndTenantId(publicUuid, tenantId);
        verify(overrideRepository).save(override);
    }

    @Test
    void createOverrideRejectsAccountNotLinkedToSelectedStudent() {
        TenantContext.set(tenantId);
        UUID userUuid = UUID.randomUUID();
        UUID studentUuid = UUID.randomUUID();
        User user = new User();
        user.setRoleSet(java.util.Set.of(UserRole.PARENT));
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setPublicUuid(studentUuid);
        when(userRepository.findByPublicUuid(userUuid)).thenReturn(Optional.of(user));
        when(studentRepository.findByPublicUuid(studentUuid)).thenReturn(Optional.of(student));
        when(studentGuardianRepository.existsActiveLinkForParent(student.getId(), userUuid)).thenReturn(false);

        assertThatThrownBy(() -> service.createOverride(
                userUuid, studentUuid, Instant.now().plusSeconds(3600), "Acuerdo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no está vinculada");
        verify(overrideRepository, never()).save(any());
    }
}
