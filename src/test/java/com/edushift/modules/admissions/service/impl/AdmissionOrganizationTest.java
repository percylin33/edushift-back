package com.edushift.modules.admissions.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admissions.dto.*;
import com.edushift.modules.admissions.entity.*;
import com.edushift.modules.admissions.mapper.AdmissionApplicantMapper;
import com.edushift.modules.admissions.repository.*;
import com.edushift.modules.students.entity.*;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionOrganizationTest {
    @Mock AdmissionApplicationRepository applicationRepository;
    @Mock AdmissionStatusEventRepository eventRepository;
    @Mock AcademicYearRepository yearRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Spy AdmissionApplicantMapper applicantMapper = new AdmissionApplicantMapper();
    @InjectMocks AdmissionServiceImpl service;
    AdmissionApplication application;
    AcademicYear year;

    @BeforeEach void setup() {
        year = new AcademicYear(); year.setPublicUuid(UUID.randomUUID());
        Grade grade = new Grade(); grade.setPublicUuid(UUID.randomUUID());
        application = new AdmissionApplication(); application.setPublicUuid(UUID.randomUUID());
        application.setAcademicYear(year); application.setGrade(grade);
        applicantMapper.update(application, applicant("90123456"));
        when(applicationRepository.findByPublicUuidForUpdate(application.getPublicUuid())).thenReturn(Optional.of(application));
        when(yearRepository.findByPublicUuidForUpdate(year.getPublicUuid())).thenReturn(Optional.of(year));
    }

    @Test void correctionRetainsStatusAndRecordsReasonWithoutIdentityValues() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        service.updateApplicant(application.getPublicUuid(), new UpdateAdmissionApplicantRequest(applicant("90123457"), "Error de transcripción"));
        assertThat(application.getStudentDocumentNumber()).isEqualTo("90123457");
        assertThat(application.getStatus()).isEqualTo(AdmissionApplicationStatus.RECEIVED);
        ArgumentCaptor<AdmissionStatusEvent> event = ArgumentCaptor.forClass(AdmissionStatusEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getNote()).contains("Error de transcripción").doesNotContain("90123456", "90123457");
        assertThat(event.getValue().getFromStatus()).isEqualTo(event.getValue().getToStatus());
    }

    @Test void correctionCannotOverwriteLinkedStudent() {
        application.setStudent(new Student());
        assertThatThrownBy(() -> service.updateApplicant(application.getPublicUuid(), new UpdateAdmissionApplicantRequest(applicant("90123457"), "Corrección")))
                .isInstanceOf(ConflictException.class);
        verify(applicationRepository, never()).saveAndFlush(any()); verifyNoInteractions(eventRepository);
    }

    @Test void admittedAndClosedApplicationsCannotBeEdited() {
        for (var state : List.of(AdmissionApplicationStatus.ADMITTED, AdmissionApplicationStatus.ENROLLED,
                AdmissionApplicationStatus.REJECTED, AdmissionApplicationStatus.WITHDRAWN)) {
            application.setStatus(state);
            assertThatThrownBy(() -> service.updateApplicant(application.getPublicUuid(), new UpdateAdmissionApplicantRequest(applicant("90123457"), "Corrección")))
                    .isInstanceOf(ConflictException.class);
        }
        verifyNoInteractions(eventRepository);
    }

    @Test void duplicateDocumentDoesNotMutateSnapshot() {
        when(applicationRepository.existsByAcademicYearAndStudentDocumentTypeAndStudentDocumentNumber(year, DocumentType.DNI, "90123457")).thenReturn(true);
        assertThatThrownBy(() -> service.updateApplicant(application.getPublicUuid(), new UpdateAdmissionApplicantRequest(applicant("90123457"), "Corrección")))
                .isInstanceOf(ConflictException.class);
        assertThat(application.getStudentDocumentNumber()).isEqualTo("90123456");
        verifyNoInteractions(eventRepository);
    }

    private CreateAdmissionFromInquiryRequest applicant(String document) {
        return new CreateAdmissionFromInquiryRequest(AdmissionSource.WEBSITE, DocumentType.DNI, document, "Alumno", "QA", null,
                LocalDate.of(2020, 1, 1), Gender.NOT_SPECIFIED, DocumentType.DNI, "90876543", "Madre", "QA", null,
                "987654321", RelationshipType.MOTHER, null, null, null);
    }
}
