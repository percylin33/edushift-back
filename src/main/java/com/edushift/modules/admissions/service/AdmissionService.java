package com.edushift.modules.admissions.service;

import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse;
import com.edushift.modules.admissions.dto.AdmissionStatusEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionApplicationRequest;
import com.edushift.modules.admissions.dto.GradeVacancyResponse;
import com.edushift.modules.admissions.dto.LinkAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.dto.TransitionAdmissionStatusRequest;
import com.edushift.modules.admissions.dto.AdmissionReservationResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationEventResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationAdvanceEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRefundRequest;
import com.edushift.modules.admissions.dto.UpdateAdmissionReservationRequest;
import java.util.List;
import java.util.UUID;

public interface AdmissionService {
    AdmissionApplicationResponse updateApplicant(UUID publicUuid, com.edushift.modules.admissions.dto.UpdateAdmissionApplicantRequest request);
    List<com.edushift.modules.admissions.dto.AdmissionSectionVacancyResponse> sectionVacancies(UUID yearPublicUuid, UUID gradePublicUuid);


	AdmissionApplicationDetailResponse detail(UUID applicationPublicUuid);

	List<AdmissionApplicationResponse> listApplications(UUID academicYearPublicUuid);

	List<GradeVacancyResponse> listVacancies(UUID academicYearPublicUuid);

	List<AdmissionReservationResponse> listReservations(UUID academicYearPublicUuid);

	AdmissionReservationResponse reserve(UUID applicationPublicUuid, CreateAdmissionReservationRequest request);

	AdmissionReservationResponse updateReservation(UUID reservationPublicUuid, UpdateAdmissionReservationRequest request);

	AdmissionReservationAdvanceResponse recordReservationAdvance(UUID reservationPublicUuid,
			RecordAdmissionReservationAdvanceRequest request);

	List<AdmissionReservationAdvanceResponse> reservationAdvances(UUID reservationPublicUuid);

	List<AdmissionReservationAdvanceEventResponse> reservationAdvanceEvents(UUID reservationPublicUuid);

	AdmissionReservationAdvanceResponse refundReservationAdvance(UUID reservationPublicUuid, UUID advancePublicUuid,
			RecordAdmissionReservationAdvanceRefundRequest request);

	List<AdmissionReservationEventResponse> reservationHistory(UUID reservationPublicUuid);

	AdmissionApplicationResponse create(UUID academicYearPublicUuid, CreateAdmissionApplicationRequest request);

	AdmissionApplicationResponse transition(UUID applicationPublicUuid, TransitionAdmissionStatusRequest request);

	AdmissionApplicationResponse linkEnrollment(UUID applicationPublicUuid, LinkAdmissionEnrollmentRequest request);

	List<AdmissionStatusEventResponse> history(UUID applicationPublicUuid);
}
