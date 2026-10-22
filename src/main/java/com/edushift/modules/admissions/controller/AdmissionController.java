package com.edushift.modules.admissions.controller;

import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse;
import com.edushift.modules.admissions.dto.CreatePresentialAdmissionRequest;
import com.edushift.modules.admissions.dto.AdmissionStatusEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionApplicationRequest;
import com.edushift.modules.admissions.dto.GradeVacancyResponse;
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
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Admissions", description = "Postulaciones, vacantes y vínculo con matrícula por año académico")
public class AdmissionController {

	private final AdmissionService admissionService;
	private final AdmissionWorkflowService workflowService;

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
	public ResponseEntity<ApiResponse<AdmissionApplicationResponse>> completeEnrollment(
			@PathVariable UUID applicationPublicUuid,
			@Valid @RequestBody CompleteAdmissionEnrollmentRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(workflowService.completeEnrollment(applicationPublicUuid, request)));
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
