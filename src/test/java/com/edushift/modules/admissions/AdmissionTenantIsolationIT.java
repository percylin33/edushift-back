package com.edushift.modules.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.admissions.dto.*;
import com.edushift.modules.admissions.entity.AdmissionContactType;
import com.edushift.modules.admissions.entity.AdmissionSource;
import com.edushift.modules.admissions.repository.AdmissionApplicationRepository;
import com.edushift.modules.admissions.service.AdmissionWorkflowService;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL tenant boundaries for inquiry continuation and direct admission. */
class AdmissionTenantIsolationIT extends IntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired com.edushift.modules.admin.plans.PlatformPlanRepository plans;
    @Autowired AdmissionWorkflowService workflow;
    @Autowired com.edushift.modules.admissions.service.AdmissionService admissions;
    @Autowired AdmissionApplicationRepository applications;
    @Autowired EntityManager entityManager;
    @Autowired com.edushift.modules.students.repository.StudentRepository students;
    @Autowired com.edushift.modules.students.repository.GuardianRepository guardians;
    @Autowired PlatformTransactionManager transactions;

    @org.springframework.test.context.DynamicPropertySource
    static void admissionTestProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        // Isolated test context only; no payment data is exercised by this suite.
        registry.add("payment.encryption.key", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }

    record Fixture(UUID tenant, UUID year, UUID grade) {}
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void anotherSchoolCannotReadConvertOrEnrollAnInquiryOrApplication() {
        Fixture schoolA = fixture();
        Fixture schoolB = fixture();
        var inquiry = TenantContext.runAs(schoolA.tenant(), () -> workflow.submitPublicInquiry(
                new PublicAdmissionInquiryRequest(schoolA.year(), schoolA.grade(), "Familia QA",
                        AdmissionContactType.PHONE, "987654321", true, "")));
        var application = TenantContext.runAs(schoolA.tenant(), () -> workflow.convertInquiry(inquiry.reference(), applicant()));
        TenantContext.runAs(schoolB.tenant(), () -> {
            assertThat(workflow.listInquiries(schoolB.year())).isEmpty();
            assertThatThrownBy(() -> admissions.detail(application.publicUuid())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> admissions.updateApplicant(application.publicUuid(), new UpdateAdmissionApplicantRequest(applicant(), "Corrección"))).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> admissions.sectionVacancies(schoolA.year(), schoolB.grade())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> admissions.sectionVacancies(schoolB.year(), schoolA.grade())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.updateInquiry(inquiry.reference(), new UpdateAdmissionInquiryRequest(null, "Atención", "Responsable QA", LocalDate.now(), "Llamar", true))).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.listInquiries(schoolA.year())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.convertInquiry(inquiry.reference(), applicant())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.completeEnrollment(application.publicUuid(),
                    new CompleteAdmissionEnrollmentRequest(UUID.randomUUID(), LocalDate.now(), null))).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.createPresential(schoolA.year(),
                    new CreatePresentialAdmissionRequest(schoolB.grade(), applicant()))).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> workflow.createPresential(schoolB.year(),
                    new CreatePresentialAdmissionRequest(schoolA.grade(), applicant()))).isInstanceOf(ResourceNotFoundException.class);
            return null;
        });
        TenantContext.runAs(schoolA.tenant(), () -> {
            assertThat(workflow.convertInquiry(inquiry.reference(), applicant()).publicUuid()).isEqualTo(application.publicUuid());
            assertThat(workflow.listInquiries(schoolA.year())).hasSize(1);
            var detail = admissions.detail(application.publicUuid());
            assertThat(detail.student().documentNumber()).isEqualTo("90123456");
            assertThat(detail.inquiry().publicUuid()).isEqualTo(inquiry.reference());
            assertThat(detail.history()).isNotEmpty();
            return null;
        });
    }

    @Test void directAdmissionNeedsNoInquiryAndDuplicateDocumentIsScopedToYearAndSchool() {
        Fixture schoolA = fixture();
        Fixture schoolB = fixture();
        TenantContext.runAs(schoolA.tenant(), () -> {
            var created = workflow.createPresential(schoolA.year(), new CreatePresentialAdmissionRequest(schoolA.grade(), applicant()));
            assertThat(created.studentPublicUuid()).isNull();
            assertThat(workflow.listInquiries(schoolA.year())).isEmpty();
            assertThatThrownBy(() -> workflow.createPresential(schoolA.year(),
                    new CreatePresentialAdmissionRequest(schoolA.grade(), applicant()))).hasMessageContaining("Ya existe");
            return null;
        });
        TenantContext.runAs(schoolB.tenant(), () -> {
            assertThat(workflow.createPresential(schoolB.year(),
                    new CreatePresentialAdmissionRequest(schoolB.grade(), applicant())).publicUuid()).isNotNull();
            return null;
        });
    }

    @Test void anAdmittedInquiryCanEnrollWithoutConfirmationAndRetryWithoutDuplicates() {
        Fixture school = fixture();
        UUID section = section(school);
        TenantContext.runAs(school.tenant(), () -> {
            var inquiry = workflow.submitPublicInquiry(new PublicAdmissionInquiryRequest(school.year(), school.grade(),
                    "Familia QA", AdmissionContactType.PHONE, "987654321", true, ""));
            var application = workflow.convertInquiry(inquiry.reference(), applicant());
            admit(application.publicUuid());
            var request = new CompleteAdmissionEnrollmentRequest(section, LocalDate.of(2026, 3, 1), null);
            var completed = workflow.completeEnrollment(application.publicUuid(), request);
            assertThat(completed.status()).isEqualTo(com.edushift.modules.admissions.entity.AdmissionApplicationStatus.ENROLLED);
            assertThat(completed.studentPublicUuid()).isNotNull();
            assertThat(completed.enrollmentPublicUuid()).isNotNull();
            assertThat(workflow.completeEnrollment(application.publicUuid(), request).enrollmentPublicUuid()).isEqualTo(completed.enrollmentPublicUuid());
            assertThat(workflow.listInquiries(school.year()).getFirst().convertedApplicationStatus())
                    .isEqualTo(com.edushift.modules.admissions.entity.AdmissionApplicationStatus.ENROLLED);
            return null;
        });
    }

    @Test void invalidEnrollmentDateRollsBackNewStudentGuardianAndApplicationLink() {
        Fixture school = fixture();
        UUID section = section(school);
        TenantContext.runAs(school.tenant(), () -> {
            var application = workflow.createPresential(school.year(), new CreatePresentialAdmissionRequest(school.grade(), applicant()));
            admit(application.publicUuid());
            assertThatThrownBy(() -> workflow.completeEnrollment(application.publicUuid(),
                    new CompleteAdmissionEnrollmentRequest(section, LocalDate.of(2025, 3, 1), null)))
                    .isInstanceOf(com.edushift.shared.exception.ConflictException.class);
            new TransactionTemplate(transactions).execute(status -> {
                assertThat(students.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "90123456")).isEmpty();
                assertThat(guardians.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "90876543")).isEmpty();
                assertThat(applications.findByPublicUuid(application.publicUuid()).orElseThrow().getStudent()).isNull();
                return null;
            });
            return null;
        });
    }

    @Test void followUpIsPersistedAuditedAndClearedOnConversion() {
        Fixture school = fixture();
        TenantContext.runAs(school.tenant(), () -> {
            var inquiry = workflow.submitPublicInquiry(new PublicAdmissionInquiryRequest(school.year(), school.grade(),
                    "Familia QA", AdmissionContactType.PHONE, "987654321", true, ""));
            var updated = workflow.updateInquiry(inquiry.reference(), new UpdateAdmissionInquiryRequest(
                    com.edushift.modules.admissions.entity.AdmissionInquiryStatus.CONTACTED, "Visita acordada", "Recepción", LocalDate.of(2026, 10, 23), "Confirmar visita", true));
            assertThat(updated.responsibleName()).isEqualTo("Recepción");
            assertThat(workflow.listInquiries(school.year()).getFirst().nextAction()).isEqualTo("Confirmar visita");
            assertThat(workflow.inquiryHistory(inquiry.reference())).anySatisfy(event -> assertThat(event.note()).contains("Confirmar visita"));
            workflow.convertInquiry(inquiry.reference(), applicant());
            assertThat(workflow.listInquiries(school.year()).getFirst().nextAttentionDate()).isNull();
            return null;
        });
    }

    private void admit(UUID uuid) {
        new TransactionTemplate(transactions).execute(status -> {
            var application = applications.findByPublicUuidForUpdate(uuid).orElseThrow();
            application.setStatus(com.edushift.modules.admissions.entity.AdmissionApplicationStatus.ADMITTED);
            applications.saveAndFlush(application); return null;
        });
    }
    private UUID section(Fixture school) {
        return TenantContext.runAs(school.tenant(), () -> new TransactionTemplate(transactions).execute(status -> {
            var year = entityManager.createQuery("from AcademicYear y where y.publicUuid = :uuid", AcademicYear.class)
                    .setParameter("uuid", school.year()).getSingleResult();
            var grade = entityManager.createQuery("from Grade g where g.publicUuid = :uuid", Grade.class)
                    .setParameter("uuid", school.grade()).getSingleResult();
            var section = new com.edushift.modules.academic.section.entity.Section();
            section.setAcademicYear(year); section.setGrade(grade); section.setName("A"); section.setCapacity(3);
            entityManager.persist(section); entityManager.flush(); return section.getPublicUuid();
        }));
    }

    private Fixture fixture() {
        var tx = new TransactionTemplate(transactions);
        Tenant tenant = tx.execute(status -> {
            Tenant created = new Tenant(); created.setName("QA Admisiones");
            created.setSlug("qa-adm-" + UUID.randomUUID().toString().substring(0, 8));
            created.setStatus(TenantStatus.ACTIVE);
            created.setPlanId(plans.findAllByOrderBySortOrderAscCodeAsc().getFirst().getId());
            return tenants.saveAndFlush(created);
        });
        return TenantContext.runAs(tenant.getId(), () -> tx.execute(status -> {
            AcademicYear year = new AcademicYear(); year.setName("2026");
            year.setStartDate(LocalDate.of(2026, 1, 1)); year.setEndDate(LocalDate.of(2026, 12, 31));
            entityManager.persist(year);
            AcademicLevel level = new AcademicLevel(); level.setName("Primaria"); level.setCode("PRIMARIA"); level.setOrdinal(1);
            entityManager.persist(level);
            Grade grade = new Grade(); grade.setName("Primero"); grade.setOrdinal(1); grade.setLevel(level);
            entityManager.persist(grade); entityManager.flush();
            return new Fixture(tenant.getId(), year.getPublicUuid(), grade.getPublicUuid());
        }));
    }
    private CreateAdmissionFromInquiryRequest applicant() {
        return new CreateAdmissionFromInquiryRequest(AdmissionSource.IN_PERSON, DocumentType.DNI, "90123456", "Alumno", "QA", null,
                LocalDate.of(2020, 1, 1), Gender.NOT_SPECIFIED, DocumentType.DNI, "90876543", "Madre", "QA", null,
                "987654321", RelationshipType.MOTHER, null, null, null);
    }
}
