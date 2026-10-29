package com.edushift.modules.admissions.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admissions.dto.*;
import com.edushift.modules.admissions.entity.*;
import com.edushift.modules.admissions.repository.*;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.students.service.StudentService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.web.FrontendLinks;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionWorkflowServiceImplTest {
    @Mock AdmissionInquiryRepository inquiryRepository;
    @Mock AdmissionInquiryEventRepository inquiryEventRepository;
    @Mock AdmissionApplicationRepository applicationRepository;
    @Mock AdmissionStatusEventRepository eventRepository;
    @Mock AcademicYearRepository yearRepository;
    @Mock GradeRepository gradeRepository;
    @Mock com.edushift.modules.academic.section.repository.SectionRepository sectionRepository;
    @Mock StudentService studentService;
    @Mock StudentRepository studentRepository;
    @Mock StudentGuardianService guardianService;
    @Mock StudentEnrollmentService enrollmentService;
    @Mock TenantRepository tenantRepository;
    @Mock NotificationService notificationService;
    @Mock com.edushift.modules.admissions.service.AdmissionReservationService reservationService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock FrontendLinks frontendLinks;
    @org.mockito.Spy com.edushift.modules.admissions.mapper.AdmissionApplicantMapper applicantMapper = new com.edushift.modules.admissions.mapper.AdmissionApplicantMapper();
    @InjectMocks AdmissionWorkflowServiceImpl service;
    AdmissionApplication application;
    AcademicYear year;
    Grade grade;
    UUID tenantId = UUID.randomUUID();

    @BeforeEach void setUp() {
        TenantContext.set(tenantId);
        year = new AcademicYear(); year.setPublicUuid(UUID.randomUUID()); year.setName("2027");
        grade = new Grade(); grade.setPublicUuid(UUID.randomUUID()); grade.setName("Primero");
        application = new AdmissionApplication();
        application.setPublicUuid(UUID.randomUUID());
        application.setAcademicYear(year); application.setGrade(grade);
        application.setStudentFirstName("Postulante"); application.setStudentLastName("Prueba");
        application.setStatus(AdmissionApplicationStatus.ADMITTED);
        application.setOfferStatus(AdmissionOfferStatus.PENDING);
        application.setOfferSentAt(Instant.now().minus(Duration.ofDays(1)));
        lenient().when(applicationRepository.findByOfferTokenHash(anyString())).thenReturn(Optional.of(application));
        lenient().when(applicationRepository.findByOfferTokenHashForUpdate(anyString())).thenReturn(Optional.of(application));
        lenient().when(applicationRepository.findByPublicUuidForUpdate(application.getPublicUuid())).thenReturn(Optional.of(application));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void legacyFamilyLinksCannotReadOrChangeAnApplication() {
        assertThatThrownBy(() -> service.getPublicOffer("Colegio", "old-token")).hasMessageContaining("no se requiere");
        assertThatThrownBy(() -> service.respondToOffer("old-token", false)).hasMessageContaining("no se requiere");
        assertThatThrownBy(() -> service.issueOfferLink(application.getPublicUuid())).hasMessageContaining("no se requiere");
        assertThat(application.getStatus()).isEqualTo(AdmissionApplicationStatus.ADMITTED);
        verifyNoInteractions(applicationRepository, notificationService);
    }
    @Test void admittingAndReminderProcessingNeverSendFamilyConfirmations() {
        service.openOffer(application);
        service.processDueOfferReminders();
        verifyNoInteractions(applicationRepository, notificationService);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AdmissionOfferStatus.class)
    void anyHistoricalOfferStatusCanEnrollWhenApplicationIsAdmitted(AdmissionOfferStatus offerStatus) {
        application.setOfferStatus(offerStatus);
        Student student = new Student(); student.setPublicUuid(UUID.randomUUID());
        application.setStudent(student);
        var section = section();
        when(sectionRepository.findByPublicUuid(section.getPublicUuid())).thenReturn(Optional.of(section));
        EnrollmentResponse enrollment = mock(EnrollmentResponse.class);
        UUID enrollmentUuid = UUID.randomUUID();
        when(enrollment.publicUuid()).thenReturn(enrollmentUuid);
        when(enrollmentService.createEnrollment(eq(student.getPublicUuid()), any())).thenReturn(enrollment);
        var result = service.completeEnrollment(application.getPublicUuid(),
                new CompleteAdmissionEnrollmentRequest(section.getPublicUuid(), LocalDate.now(), null));
        assertThat(result.status()).isEqualTo(AdmissionApplicationStatus.ENROLLED);
        assertThat(result.enrollmentPublicUuid()).isEqualTo(enrollmentUuid);
        verifyNoInteractions(studentService, notificationService);
    }
    @Test void enrollmentRequiresAdmissionAndCorrectYearAndGrade() {
        application.setStatus(AdmissionApplicationStatus.UNDER_REVIEW);
        assertThatThrownBy(() -> service.completeEnrollment(application.getPublicUuid(),
                new CompleteAdmissionEnrollmentRequest(UUID.randomUUID(), LocalDate.now(), null))).hasMessageContaining("admitida");
        application.setStatus(AdmissionApplicationStatus.ADMITTED);
        var section = section();
        Grade other = new Grade(); other.setPublicUuid(UUID.randomUUID()); section.setGrade(other);
        when(sectionRepository.findByPublicUuid(section.getPublicUuid())).thenReturn(Optional.of(section));
        assertThatThrownBy(() -> service.completeEnrollment(application.getPublicUuid(),
                new CompleteAdmissionEnrollmentRequest(section.getPublicUuid(), LocalDate.now(), null))).hasMessageContaining("grado");
        verifyNoInteractions(studentService, guardianService, enrollmentService);
    }
    @Test void enrollmentRetryDoesNotDuplicateStudentOrEnrollment() {
        application.setStatus(AdmissionApplicationStatus.ENROLLED);
        application.setEnrollmentPublicUuid(UUID.randomUUID());
        var result = service.completeEnrollment(application.getPublicUuid(), new CompleteAdmissionEnrollmentRequest(UUID.randomUUID(), LocalDate.now(), null));
        assertThat(result.enrollmentPublicUuid()).isEqualTo(application.getEnrollmentPublicUuid());
        verifyNoInteractions(studentService, guardianService, enrollmentService);
    }
    @Test void publicInquiryNeverCreatesStudentOrEnrollmentOrReservesSeat() {
        when(yearRepository.findByPublicUuid(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(gradeRepository.findByPublicUuid(grade.getPublicUuid())).thenReturn(Optional.of(grade));
        when(inquiryRepository.saveAndFlush(any())).thenAnswer(call -> {
            AdmissionInquiry inquiry = call.getArgument(0);
            inquiry.setPublicUuid(UUID.randomUUID()); return inquiry;
        });
        service.submitPublicInquiry(new PublicAdmissionInquiryRequest(year.getPublicUuid(), grade.getPublicUuid(), "Familia", AdmissionContactType.PHONE, "987654321", true, ""));
        verifyNoInteractions(applicationRepository, studentService, guardianService, enrollmentService);
    }
    @Test void honeypotDoesNotPersistAnything() {
        service.submitPublicInquiry(new PublicAdmissionInquiryRequest(year.getPublicUuid(), grade.getPublicUuid(), "Bot", AdmissionContactType.PHONE, "987654321", true, "spam"));
        verifyNoInteractions(inquiryRepository, applicationRepository, studentService);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AdmissionInquiryStatus.class, names = {"RECEIVED", "CONTACTED", "QUALIFIED"})
    void canContinueFromAnyOpenInquiryWithoutQualification(AdmissionInquiryStatus status) {
        var inquiry = inquiry(status);
        stubCreation();
        when(inquiryRepository.findByPublicUuidForUpdate(inquiry.getPublicUuid())).thenReturn(Optional.of(inquiry));
        var result = service.convertInquiry(inquiry.getPublicUuid(), applicant());
        assertThat(result.status()).isEqualTo(AdmissionApplicationStatus.RECEIVED);
        assertThat(inquiry.getStatus()).isEqualTo(AdmissionInquiryStatus.CONVERTED);
        assertThat(inquiry.getConvertedApplicationId()).isNotNull();
        verifyNoInteractions(studentService, enrollmentService, notificationService);
    }
    @Test void retryReturnsExistingApplicationWithoutCreatingAnother() {
        var inquiry = inquiry(AdmissionInquiryStatus.CONVERTED);
        inquiry.setConvertedApplicationId(UUID.randomUUID());
        when(inquiryRepository.findByPublicUuidForUpdate(inquiry.getPublicUuid())).thenReturn(Optional.of(inquiry));
        when(applicationRepository.findScopedById(inquiry.getConvertedApplicationId())).thenReturn(Optional.of(application));
        assertThat(service.convertInquiry(inquiry.getPublicUuid(), applicant()).publicUuid()).isEqualTo(application.getPublicUuid());
        verify(applicationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(studentService, enrollmentService);
    }
    @Test void closedInquiryCannotBeConverted() {
        var inquiry = inquiry(AdmissionInquiryStatus.CLOSED);
        when(inquiryRepository.findByPublicUuidForUpdate(inquiry.getPublicUuid())).thenReturn(Optional.of(inquiry));
        assertThatThrownBy(() -> service.convertInquiry(inquiry.getPublicUuid(), applicant())).hasMessageContaining("cerrada");
        verify(applicationRepository, never()).saveAndFlush(any());
    }
    @Test void presentialAdmissionNeedsNoInquiryOrStudentRecord() {
        stubCreation();
        when(yearRepository.findByPublicUuid(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(gradeRepository.findByPublicUuid(grade.getPublicUuid())).thenReturn(Optional.of(grade));
        var result = service.createPresential(year.getPublicUuid(), new CreatePresentialAdmissionRequest(grade.getPublicUuid(), applicant()));
        assertThat(result.studentPublicUuid()).isNull();
        assertThat(result.offerStatus()).isEqualTo(AdmissionOfferStatus.NOT_REQUIRED);
        verifyNoInteractions(inquiryRepository, studentService, enrollmentService, notificationService);
    }
    @Test void duplicateDocumentForYearIsRejectedBeforeCreation() {
        when(yearRepository.findByPublicUuid(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(yearRepository.findByPublicUuidForUpdate(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(gradeRepository.findByPublicUuid(grade.getPublicUuid())).thenReturn(Optional.of(grade));
        when(applicationRepository.existsByAcademicYearAndStudentDocumentTypeAndStudentDocumentNumber(any(), any(), anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.createPresential(year.getPublicUuid(), new CreatePresentialAdmissionRequest(grade.getPublicUuid(), applicant()))).hasMessageContaining("Ya existe");
        verify(applicationRepository, never()).saveAndFlush(any());
    }
    @Test void notesAreAuditedEvenWithoutStatusChange() {
        var inquiry = inquiry(AdmissionInquiryStatus.CONTACTED);
        when(inquiryRepository.findByPublicUuidForUpdate(inquiry.getPublicUuid())).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.save(inquiry)).thenReturn(inquiry);
        service.updateInquiry(inquiry.getPublicUuid(), new UpdateAdmissionInquiryRequest(AdmissionInquiryStatus.CONTACTED, "Presencial: visita"));
        var event = org.mockito.ArgumentCaptor.forClass(AdmissionInquiryEvent.class);
        verify(inquiryEventRepository).save(event.capture());
        assertThat(event.getValue().getNote()).isEqualTo("Presencial: visita");
    }
    @Test void convertedInquiryDisplaysActualEnrollmentStatus() {
        var inquiry = inquiry(AdmissionInquiryStatus.CONVERTED); inquiry.setConvertedApplicationId(UUID.randomUUID());
        application.setStatus(AdmissionApplicationStatus.ENROLLED);
        when(yearRepository.findByPublicUuid(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(inquiryRepository.findAllByAcademicYearOrderByCreatedAtDesc(year)).thenReturn(List.of(inquiry));
        when(applicationRepository.findScopedById(inquiry.getConvertedApplicationId())).thenReturn(Optional.of(application));
        assertThat(service.listInquiries(year.getPublicUuid()).get(0).convertedApplicationStatus()).isEqualTo(AdmissionApplicationStatus.ENROLLED);
    }
    private com.edushift.modules.academic.section.entity.Section section() {
        var section = new com.edushift.modules.academic.section.entity.Section();
        section.setPublicUuid(UUID.randomUUID()); section.setGrade(grade); section.setAcademicYear(year); return section;
    }
    private AdmissionInquiry inquiry(AdmissionInquiryStatus status) {
        var inquiry = new AdmissionInquiry(); inquiry.setPublicUuid(UUID.randomUUID()); inquiry.setAcademicYear(year);
        inquiry.setGrade(grade); inquiry.setStatus(status); return inquiry;
    }
    private void stubCreation() {
        when(yearRepository.findByPublicUuidForUpdate(year.getPublicUuid())).thenReturn(Optional.of(year));
        when(applicationRepository.saveAndFlush(any())).thenAnswer(call -> {
            AdmissionApplication created = call.getArgument(0); created.setId(UUID.randomUUID()); created.setPublicUuid(UUID.randomUUID()); return created;
        });
    }
    private CreateAdmissionFromInquiryRequest applicant() {
        return new CreateAdmissionFromInquiryRequest(AdmissionSource.IN_PERSON,
            com.edushift.modules.students.entity.DocumentType.DNI, "12345678", "Alumno", "Prueba", null,
            LocalDate.of(2020, 1, 1), com.edushift.modules.students.entity.Gender.NOT_SPECIFIED,
            com.edushift.modules.students.entity.DocumentType.DNI, "87654321", "Madre", "Prueba", "test@example.test",
            "987654321", com.edushift.modules.students.entity.RelationshipType.MOTHER, null, null, null);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   ", "-------", "123456", "1234567890123456", "abc987654321", "family@example.test"})
    void rejectsMissingOrInvalidPhoneBeforePersisting(String phone) {
        var request = new PublicAdmissionInquiryRequest(year.getPublicUuid(), grade.getPublicUuid(), "Familia",
                AdmissionContactType.PHONE, phone, true, "");
        assertThatThrownBy(() -> service.submitPublicInquiry(request)).hasMessageContaining("teléfono válido");
        verifyNoInteractions(inquiryRepository, yearRepository, gradeRepository);
    }
    @Test void publicInquiryRequiresPhoneEvenWhenEmailIsProvided() {
        var request = new PublicAdmissionInquiryRequest(year.getPublicUuid(), grade.getPublicUuid(), "Familia",
                AdmissionContactType.EMAIL, "family@example.test", true, "");
        assertThatThrownBy(() -> service.submitPublicInquiry(request)).hasMessageContaining("teléfono de contacto");
        verifyNoInteractions(inquiryRepository);
    }
    @Test void blankFamilyNameCannotBeSubmittedWithAValidPhone() {
        var request = new PublicAdmissionInquiryRequest(year.getPublicUuid(), grade.getPublicUuid(), "   ",
                AdmissionContactType.PHONE, "987654321", true, "");
        assertThatThrownBy(() -> service.submitPublicInquiry(request)).hasMessageContaining("nombre de la familia");
        verifyNoInteractions(inquiryRepository);
    }

}
