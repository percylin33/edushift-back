package com.edushift.modules.admissions.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.admissions.entity.*;
import com.edushift.modules.admissions.repository.*;
import com.edushift.modules.students.entity.*;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.service.StudentGuardianService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionApplicationDetailTest {
    @Mock AdmissionApplicationRepository applicationRepository;
    @Mock AdmissionStatusEventRepository eventRepository;
    @Mock AdmissionInquiryEventRepository inquiryEventRepository;
    @Mock StudentGuardianService guardianService;
    @org.mockito.Spy com.edushift.modules.admissions.mapper.AdmissionApplicantMapper applicantMapper = new com.edushift.modules.admissions.mapper.AdmissionApplicantMapper();
    @InjectMocks AdmissionServiceImpl service;
    AdmissionApplication application;
    @BeforeEach void setup() {
        var year = new AcademicYear(); year.setPublicUuid(UUID.randomUUID()); year.setName("2026");
        var grade = new Grade(); grade.setPublicUuid(UUID.randomUUID()); grade.setName("Primero");
        application = new AdmissionApplication(); application.setPublicUuid(UUID.randomUUID());
        application.setAcademicYear(year); application.setGrade(grade);
        when(applicationRepository.findByPublicUuid(application.getPublicUuid())).thenReturn(Optional.of(application));
    }
    @Test void returnsFormalSnapshotAndOriginNotesWithoutChangingStatusOrExposingToken() throws Exception {
        application.setStatus(AdmissionApplicationStatus.RECEIVED);
        application.setStudentFirstName("Alumno"); application.setStudentLastName("QA");
        application.setStudentDocumentType(DocumentType.DNI); application.setStudentDocumentNumber("90000101");
        application.setStudentBirthDate(LocalDate.of(2020, 1, 1));
        application.setGuardianDocumentNumber("90000201"); application.setGuardianFirstName("Madre");
        application.setGuardianLastName("QA"); application.setGuardianPhone("987654321");
        application.setOfferTokenHash("private-hash-never-expose");
        var inquiry = new AdmissionInquiry(); inquiry.setPublicUuid(UUID.randomUUID());
        inquiry.setAcademicYear(application.getAcademicYear()); inquiry.setGrade(application.getGrade());
        inquiry.setContactName("Familia QA"); inquiry.setContactValue("987654321"); application.setInquiry(inquiry);
        var note = new AdmissionInquiryEvent(); note.setNote("Presencial: visitó el colegio");
        when(inquiryEventRepository.findAllByInquiryOrderByCreatedAtAsc(inquiry)).thenReturn(List.of(note));
        var event = new AdmissionStatusEvent(); event.setToStatus(AdmissionApplicationStatus.RECEIVED);
        when(eventRepository.findAllByApplicationOrderByCreatedAtAsc(application)).thenReturn(List.of(event));
        var detail = service.detail(application.getPublicUuid());
        assertThat(detail.student().fullName()).isEqualTo("Alumno QA");
        assertThat(detail.student().documentNumber()).isEqualTo("90000101");
        assertThat(detail.guardians().getFirst().phone()).isEqualTo("987654321");
        assertThat(detail.inquiry().history().getFirst().note()).contains("visitó");
        assertThat(detail.history()).hasSize(1);
        assertThat(application.getStatus()).isEqualTo(AdmissionApplicationStatus.RECEIVED);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(detail);
        assertThat(json).doesNotContain("private-hash", "tenantId", "offerTokenHash");
        verify(applicationRepository, never()).save(any());
        verifyNoInteractions(guardianService);
    }
    @Test void legacyApplicationUsesExistingStudentAndGuardiansWithoutInquiry() {
        var student = new Student(); student.setPublicUuid(UUID.randomUUID()); student.setFirstName("Alumno"); student.setLastName("Anterior");
        student.setDocumentType(DocumentType.DNI); student.setDocumentNumber("90000102"); application.setStudent(student);
        when(guardianService.listGuardians(student.getPublicUuid())).thenReturn(List.of(new GuardianResponse(
            UUID.randomUUID(), UUID.randomUUID(), DocumentType.DNI, "90000201", "Madre", "QA", "Madre QA",
            null, "987654321", null, RelationshipType.MOTHER, true, false, null, null)));
        var detail = service.detail(application.getPublicUuid());
        assertThat(detail.student().documentNumber()).isEqualTo("90000102");
        assertThat(detail.guardians()).hasSize(1);
        assertThat(detail.inquiry()).isNull();
        verifyNoInteractions(inquiryEventRepository);
    }
    @Test void missingDataProducesEmptySectionsWithoutInventingProfiles() {
        var detail = service.detail(application.getPublicUuid());
        assertThat(detail.student().documentNumber()).isNull();
        assertThat(detail.guardians()).isEmpty(); assertThat(detail.inquiry()).isNull();
    }
    @Test void unavailableApplicationDoesNotLoadAnyPersonalData() {
        when(applicationRepository.findByPublicUuid(application.getPublicUuid())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(application.getPublicUuid()))
            .isInstanceOf(com.edushift.shared.exception.ResourceNotFoundException.class);
        verifyNoInteractions(eventRepository, inquiryEventRepository, guardianService);
    }
}
