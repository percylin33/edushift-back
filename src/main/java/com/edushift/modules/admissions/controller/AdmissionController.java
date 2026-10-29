package com.edushift.modules.admissions.controller;

import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.AdmissionEnrollmentCompletionResponse;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse;
import com.edushift.modules.admissions.dto.CreatePresentialAdmissionRequest;
import com.edushift.modules.admissions.dto.CreatePresentialInquiryRequest;
import com.edushift.modules.admissions.dto.AdmissionStatusEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionApplicationRequest;
import com.edushift.modules.admissions.dto.GradeVacancyResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationEventResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRefundRequest;
import com.edushift.modules.admissions.dto.UpdateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.LinkAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.dto.TransitionAdmissionStatusRequest;
import com.edushift.modules.admissions.dto.AdmissionInquiryResponse;
import com.edushift.modules.admissions.dto.AdmissionInquiryEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionFromInquiryRequest;
import com.edushift.modules.admissions.dto.OfferLinkResponse;
import com.edushift.modules.admissions.dto.UpdateAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.CompleteAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.service.AdmissionService;
import com.edushift.modules.admissions.service.AdmissionWorkflowService;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.students.dto.GuardianPortalAccessResponse;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admissions")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admissions", description = "Postulaciones, vacantes y vínculo con matrícula por año académico")
public class AdmissionController {

	private final AdmissionService admissionService;
	private final AdmissionWorkflowService workflowService;
	private final AdminPaymentService adminPaymentService;
	private final StudentGuardianService guardianService;

    @PatchMapping("/applications/{applicationPublicUuid}/applicant")
    @PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Corregir datos de un expediente abierto con motivo auditable, sin modificar fichas vinculadas")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expediente corregido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos o motivo inválidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Sin permiso de gestión"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Solicitud no disponible en este colegio"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Estado no editable o documento duplicado")
    })
    public ApiResponse<AdmissionApplicationResponse> updateApplicant(@PathVariable UUID applicationPublicUuid,
            @Valid @RequestBody com.edushift.modules.admissions.dto.UpdateAdmissionApplicantRequest request) {
        return ApiResponse.ok(admissionService.updateApplicant(applicationPublicUuid, request));
    }

    @GetMapping("/years/{yearPublicUuid}/grades/{gradePublicUuid}/sections")
    @PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar aforo y ocupación por sección del año y grado del colegio actual")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Capacidad por sección"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Sin permiso de lectura"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Año o grado no disponible")
    })
    public ApiResponse<List<com.edushift.modules.admissions.dto.AdmissionSectionVacancyResponse>> sectionVacancies(
            @PathVariable UUID yearPublicUuid, @PathVariable UUID gradePublicUuid) {
        return ApiResponse.ok(admissionService.sectionVacancies(yearPublicUuid, gradePublicUuid));
    }

	@GetMapping("/applications/{applicationPublicUuid}")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar expediente, consulta de origen e historial de una solicitud del colegio actual")
	@io.swagger.v3.oas.annotations.responses.ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expediente"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Sin permiso de lectura"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Solicitud no encontrada en el colegio")
	})
	public ApiResponse<AdmissionApplicationDetailResponse> detail(@PathVariable UUID applicationPublicUuid) {
		return ApiResponse.ok(admissionService.detail(applicationPublicUuid));
	}

	@GetMapping("/years/{yearPublicUuid}/inquiries")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_INQUIRIES_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Listar consultas recibidas por el colegio para un año y grado")
	public ResponseEntity<List<AdmissionInquiryResponse>> listInquiries(@PathVariable UUID yearPublicUuid) {
		return ResponseEntity.ok(workflowService.listInquiries(yearPublicUuid));
	}

	@PostMapping("/years/{yearPublicUuid}/inquiries")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_INQUIRIES_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar una consulta presencial del colegio sin crear postulación ni ocupar vacante")
	public ResponseEntity<ApiResponse<AdmissionInquiryResponse>> createPresentialInquiry(
			@PathVariable UUID yearPublicUuid,
			@Valid @RequestBody CreatePresentialInquiryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(workflowService.createPresentialInquiry(yearPublicUuid, request)));
	}

	@PatchMapping("/inquiries/{inquiryPublicUuid}")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_INQUIRIES_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Actualizar estado de seguimiento de una consulta")
	public ResponseEntity<ApiResponse<AdmissionInquiryResponse>> updateInquiry(
			@PathVariable UUID inquiryPublicUuid,
			@Valid @RequestBody UpdateAdmissionInquiryRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(workflowService.updateInquiry(inquiryPublicUuid, request)));
	}

	@GetMapping("/inquiries/{inquiryPublicUuid}/history")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_INQUIRIES_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar el historial inmutable de seguimiento de una consulta")
	public ResponseEntity<List<AdmissionInquiryEventResponse>> inquiryHistory(@PathVariable UUID inquiryPublicUuid) {
		return ResponseEntity.ok(workflowService.inquiryHistory(inquiryPublicUuid));
	}

	@PostMapping("/inquiries/{inquiryPublicUuid}/application")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Continuar admisión desde una consulta y capturar documentos de identidad")
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> convertInquiry(
			@PathVariable UUID inquiryPublicUuid,
			@Valid @RequestBody CreateAdmissionFromInquiryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(workflowService.convertInquiry(inquiryPublicUuid, request)));
	}

	@PostMapping("/years/{yearPublicUuid}/presential-applications")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Iniciar admisión presencial sin consulta previa ni ficha de estudiante")
	@io.swagger.v3.oas.annotations.responses.ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Admisión creada"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos incompletos"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Postulación duplicada o año cerrado")
	})
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> createPresential(
			@PathVariable UUID yearPublicUuid,
			@Valid @RequestBody CreatePresentialAdmissionRequest request) {
		var application = workflowService.createPresential(yearPublicUuid, request);
		return ResponseEntity.created(java.net.URI.create("/api/v1/admissions/years/" + yearPublicUuid + "/applications"))
				.body(ApiResponse.ok(application));
	}

	@PostMapping("/applications/{applicationPublicUuid}/offer-link")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_OFFERS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Emitir o renovar el enlace privado para responder a la oferta")
	public ResponseEntity<ApiResponse<OfferLinkResponse>> issueOfferLink(@PathVariable UUID applicationPublicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(workflowService.issueOfferLink(applicationPublicUuid)));
	}

	@PostMapping("/applications/{applicationPublicUuid}/complete-enrollment")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_ENROLL')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Completar matrícula de una solicitud admitida sin confirmación familiar")
	public ResponseEntity<ApiResponse<AdmissionEnrollmentCompletionResponse>> completeEnrollment(
			@PathVariable UUID applicationPublicUuid,
			@Valid @RequestBody CompleteAdmissionEnrollmentRequest request) {
		AdmissionApplicationResponse application = workflowService.completeEnrollment(applicationPublicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(enrollmentSideEffects(application, applicationPublicUuid)));
	}

	@PostMapping("/applications/{applicationPublicUuid}/retry-enrollment-setup")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_ENROLL')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Reintentar vinculación del portal y emisión idempotente del cargo tras una matrícula")
	public ResponseEntity<ApiResponse<AdmissionEnrollmentCompletionResponse>> retryEnrollmentSetup(
			@PathVariable UUID applicationPublicUuid) {
		AdmissionApplicationResponse application = workflowService.enrolledApplicationForRetry(applicationPublicUuid);
		return ResponseEntity.ok(ApiResponse.ok(enrollmentSideEffects(application, applicationPublicUuid)));
	}

	private AdmissionEnrollmentCompletionResponse enrollmentSideEffects(AdmissionApplicationResponse application,
			UUID applicationPublicUuid) {
		AdmissionEnrollmentCompletionResponse.BillingStatus billingStatus;
		com.edushift.modules.payments.dto.InvoiceResponse invoice = null;
		GuardianPortalAccessResponse.Status portalAccessStatus = GuardianPortalAccessResponse.Status.PENDING;
		try {
			portalAccessStatus = guardianService.ensurePortalAccess(application.studentPublicUuid()).status();
		}
		catch (RuntimeException exception) {
			log.warn("Enrollment completed but guardian portal provisioning remains pending for application {} ({})",
					applicationPublicUuid, exception.getClass().getSimpleName());
		}
		try {
			invoice = adminPaymentService.emitEnrollmentInvoice(application.publicUuid(), application.studentPublicUuid(),
					application.academicYearPublicUuid(), application.academicYearName());
			billingStatus = invoice == null
					? AdmissionEnrollmentCompletionResponse.BillingStatus.NOT_CONFIGURED
					: invoice.status() == com.edushift.modules.payments.entity.Invoice.Status.PAID
							? AdmissionEnrollmentCompletionResponse.BillingStatus.PAID
							: AdmissionEnrollmentCompletionResponse.BillingStatus.PENDING;
		}
		catch (RuntimeException exception) {
			log.error("Enrollment completed but enrollment invoice issuance failed for application {} ({})",
					applicationPublicUuid, exception.getClass().getSimpleName());
			billingStatus = AdmissionEnrollmentCompletionResponse.BillingStatus.ISSUE_FAILED;
		}
		return new AdmissionEnrollmentCompletionResponse(application, billingStatus, invoice, portalAccessStatus);
	}

	@GetMapping("/years/{yearPublicUuid}/applications")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Listar postulaciones de un año académico del colegio actual")
	public ResponseEntity<List<AdmissionApplicationResponse>> listApplications(@PathVariable UUID yearPublicUuid) {
		return ResponseEntity.ok(admissionService.listApplications(yearPublicUuid));
	}

	@GetMapping("/years/{yearPublicUuid}/vacancies")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar aforo, matrícula activa, postulantes admitidos y vacantes por grado")
	public ResponseEntity<List<GradeVacancyResponse>> listVacancies(@PathVariable UUID yearPublicUuid) {
		return ResponseEntity.ok(admissionService.listVacancies(yearPublicUuid));
	}

	@GetMapping("/years/{yearPublicUuid}/reservations")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Listar separaciones de vacante e identificar las vencidas del año")
	public ResponseEntity<List<AdmissionReservationResponse>> listReservations(@PathVariable UUID yearPublicUuid) {
		return ResponseEntity.ok(admissionService.listReservations(yearPublicUuid));
	}

	@PostMapping("/applications/{applicationPublicUuid}/reservations")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Separar temporalmente una vacante para una postulación")
	public ResponseEntity<ApiResponse<AdmissionReservationResponse>> reserve(
			@PathVariable UUID applicationPublicUuid,
			@Valid @RequestBody CreateAdmissionReservationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(admissionService.reserve(applicationPublicUuid, request)));
	}

	@PatchMapping("/reservations/{reservationPublicUuid}")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Liberar una separación temporal o comprometida")
	public ResponseEntity<ApiResponse<AdmissionReservationResponse>> updateReservation(
			@PathVariable UUID reservationPublicUuid,
			@Valid @RequestBody UpdateAdmissionReservationRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(admissionService.updateReservation(reservationPublicUuid, request)));
	}

	@GetMapping("/reservations/{reservationPublicUuid}/history")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar historial inmutable de una separación de vacante")
	public ResponseEntity<List<AdmissionReservationEventResponse>> reservationHistory(
			@PathVariable UUID reservationPublicUuid) {
		return ResponseEntity.ok(admissionService.reservationHistory(reservationPublicUuid));
	}

	@GetMapping("/reservations/{reservationPublicUuid}/advances")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ') or hasRole('TENANT_ADMIN') or hasAnyAuthority('LMS_PAYMENT_ADMIN','PAYMENTS:READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar anticipos y saldos de una separación")
	public ResponseEntity<List<AdmissionReservationAdvanceResponse>> reservationAdvances(
			@PathVariable UUID reservationPublicUuid) {
		return ResponseEntity.ok(admissionService.reservationAdvances(reservationPublicUuid));
	}

	@GetMapping("/reservations/{reservationPublicUuid}/advances/events")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ') or hasRole('TENANT_ADMIN') or hasAnyAuthority('LMS_PAYMENT_ADMIN','PAYMENTS:READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar el historial inmutable de anticipos y aplicaciones de una separación")
	public ResponseEntity<List<com.edushift.modules.admissions.dto.AdmissionReservationAdvanceEventResponse>> reservationAdvanceEvents(
			@PathVariable UUID reservationPublicUuid) {
		return ResponseEntity.ok(admissionService.reservationAdvanceEvents(reservationPublicUuid));
	}

	@PostMapping("/reservations/{reservationPublicUuid}/advances")
	@PreAuthorize("hasRole('TENANT_ADMIN') or hasAnyAuthority('LMS_PAYMENT_ADMIN','PAYMENTS:RECEIVE')")
	@SecurityRequirement(name = "bearerAuth")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar dinero recibido al separar una vacante")
	public ResponseEntity<ApiResponse<AdmissionReservationAdvanceResponse>> recordReservationAdvance(
			@PathVariable UUID reservationPublicUuid,
			@Valid @RequestBody RecordAdmissionReservationAdvanceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
				admissionService.recordReservationAdvance(reservationPublicUuid, request)));
	}

	@PostMapping("/reservations/{reservationPublicUuid}/advances/{advancePublicUuid}/refund")
	@PreAuthorize("hasRole('TENANT_ADMIN') or hasAnyAuthority('LMS_PAYMENT_ADMIN','PAYMENTS:RECEIVE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Registrar la devolución total de un anticipo aún no aplicado")
	public ResponseEntity<ApiResponse<AdmissionReservationAdvanceResponse>> refundReservationAdvance(
			@PathVariable UUID reservationPublicUuid,
			@PathVariable UUID advancePublicUuid,
			@Valid @RequestBody RecordAdmissionReservationAdvanceRefundRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(admissionService.refundReservationAdvance(
				reservationPublicUuid, advancePublicUuid, request)));
	}

	@PostMapping("/years/{yearPublicUuid}/applications")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_MANAGE')")
	@SecurityRequirement(name = "bearerAuth")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar una postulación sin crear todavía una matrícula")
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> create(
			@PathVariable UUID yearPublicUuid,
			@Valid @RequestBody CreateAdmissionApplicationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(admissionService.create(yearPublicUuid, request)));
	}

	@PatchMapping("/applications/{applicationPublicUuid}/status")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_DECIDE')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Avanzar o resolver una postulación; cada cambio queda en el historial")
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> transition(
			@PathVariable UUID applicationPublicUuid,
			@Valid @RequestBody TransitionAdmissionStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(admissionService.transition(applicationPublicUuid, request)));
	}

	@PostMapping("/applications/{applicationPublicUuid}/enrollment")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_ENROLL')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Vincular una matrícula activa previamente registrada en el módulo de estudiantes")
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> linkEnrollment(
			@PathVariable UUID applicationPublicUuid,
			@Valid @RequestBody LinkAdmissionEnrollmentRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(admissionService.linkEnrollment(applicationPublicUuid, request)));
	}

	@GetMapping("/applications/{applicationPublicUuid}/history")
	@PreAuthorize("hasAnyAuthority('LMS_ADMISSION_MANAGE','ADMISSIONS_APPLICATIONS_READ')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Consultar el historial auditable de estados de una postulación")
	public ResponseEntity<List<AdmissionStatusEventResponse>> history(@PathVariable UUID applicationPublicUuid) {
		return ResponseEntity.ok(admissionService.history(applicationPublicUuid));
	}
}
