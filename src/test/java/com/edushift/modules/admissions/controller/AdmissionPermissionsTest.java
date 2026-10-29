package com.edushift.modules.admissions.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.edushift.modules.admissions.service.AdmissionService;
import com.edushift.modules.admissions.service.AdmissionWorkflowService;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.students.service.StudentGuardianService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdmissionPermissionsTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean AdmissionService admissions() { return mock(AdmissionService.class); }
        @Bean AdmissionWorkflowService workflow() { return mock(AdmissionWorkflowService.class); }
        @Bean AdminPaymentService adminPayments() { return mock(AdminPaymentService.class); }
        @Bean StudentGuardianService guardians() { return mock(StudentGuardianService.class); }
        @Bean AdmissionController controller(AdmissionService admissions, AdmissionWorkflowService workflow,
                AdminPaymentService adminPayments, StudentGuardianService guardians) {
            return new AdmissionController(admissions, workflow, adminPayments, guardians);
        }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("staff", null,
                List.of(new SimpleGrantedAuthority(authority))));
    }
    @Test void inquiryReaderCannotIssueOfferOrEnroll() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_INQUIRIES_READ");
            var controller = context.getBean(AdmissionController.class);
            assertThatThrownBy(() -> controller.issueOfferLink(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.completeEnrollment(UUID.randomUUID(), null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AdmissionWorkflowService.class));
        }
    }
    @Test void offerManagerCannotEnroll() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_OFFERS_MANAGE");
            UUID uuid = UUID.randomUUID();
            var controller = context.getBean(AdmissionController.class);
            controller.issueOfferLink(uuid);
            verify(context.getBean(AdmissionWorkflowService.class)).issueOfferLink(uuid);
            assertThatThrownBy(() -> controller.completeEnrollment(uuid, null)).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void inquiryReaderCannotCreatePresentialAdmission() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_INQUIRIES_READ");
            assertThatThrownBy(() -> context.getBean(AdmissionController.class).createPresential(UUID.randomUUID(), null))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AdmissionWorkflowService.class));
        }
    }
    @Test void applicationsManagerCanCreatePresentialAdmission() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_APPLICATIONS_MANAGE");
            UUID year = UUID.randomUUID();
            var response = org.mockito.Mockito.mock(com.edushift.modules.admissions.dto.AdmissionApplicationResponse.class);
            when(context.getBean(AdmissionWorkflowService.class).createPresential(year, null)).thenReturn(response);
            org.assertj.core.api.Assertions.assertThat(context.getBean(AdmissionController.class).createPresential(year, null).getStatusCode().value()).isEqualTo(201);
            verify(context.getBean(AdmissionWorkflowService.class)).createPresential(year, null);
        }
    }
    @Test void applicationReaderCanReadDetailButCannotDecide() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_APPLICATIONS_READ"); UUID uuid = UUID.randomUUID();
            var controller = context.getBean(AdmissionController.class);
            controller.detail(uuid);
            verify(context.getBean(AdmissionService.class)).detail(uuid);
            assertThatThrownBy(() -> controller.transition(uuid, null)).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void inquiryReaderCannotReadApplicationPersonalData() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_INQUIRIES_READ");
            assertThatThrownBy(() -> context.getBean(AdmissionController.class).detail(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AdmissionService.class));
        }
    }
    @Test void applicationReaderCannotCorrectApplicantButCanReadCapacity() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_APPLICATIONS_READ"); UUID uuid = UUID.randomUUID();
            var controller = context.getBean(AdmissionController.class);
            assertThatThrownBy(() -> controller.updateApplicant(uuid, null)).isInstanceOf(AccessDeniedException.class);
            controller.sectionVacancies(uuid, uuid);
            verify(context.getBean(AdmissionService.class)).sectionVacancies(uuid, uuid);
        }
    }
    @Test void applicationManagerCanCorrectButCannotEnrollOrDecide() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_APPLICATIONS_MANAGE"); UUID uuid = UUID.randomUUID();
            var controller = context.getBean(AdmissionController.class);
            controller.updateApplicant(uuid, null);
            verify(context.getBean(AdmissionService.class)).updateApplicant(uuid, null);
            assertThatThrownBy(() -> controller.completeEnrollment(uuid, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.transition(uuid, null)).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void enrollmentAuthorityCanCompleteEnrollment() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMISSIONS_ENROLL");
            UUID uuid = UUID.randomUUID();
            context.getBean(AdmissionController.class).completeEnrollment(uuid, null);
            verify(context.getBean(AdmissionWorkflowService.class)).completeEnrollment(uuid, null);
        }
    }
}
