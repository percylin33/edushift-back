package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.ApplyDayPlanRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayBlockResponse;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.service.DayScheduleTemplateService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Day Templates",
		description = "School-day structure (recess/lunch/assembly) per year + level")
public class DayScheduleTemplateController {

	private final DayScheduleTemplateService service;

	@GetMapping("/schedule/day-templates")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List day schedule templates for an academic year")
	public ResponseEntity<ApiResponse<List<DayTemplateResponse>>> list(
			@RequestParam UUID yearUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.listByYear(yearUuid)));
	}

	@PostMapping("/schedule/day-templates")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Create a day schedule template")
	public ResponseEntity<ApiResponse<DayTemplateResponse>> create(
			@Valid @RequestBody CreateDayTemplateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.create(request)));
	}

	@GetMapping("/schedule/day-templates/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a day schedule template with blocks")
	public ResponseEntity<ApiResponse<DayTemplateResponse>> get(@PathVariable UUID uuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.get(uuid)));
	}

	@PutMapping("/schedule/day-templates/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a day schedule template")
	public ResponseEntity<ApiResponse<DayTemplateResponse>> update(
			@PathVariable UUID uuid,
			@Valid @RequestBody UpdateDayTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.update(uuid, request)));
	}

	@DeleteMapping("/schedule/day-templates/{uuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a day schedule template and its blocks")
	public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
		service.delete(uuid);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/schedule/day-templates/{uuid}/blocks")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Add a block under a day schedule template")
	public ResponseEntity<ApiResponse<DayBlockResponse>> addBlock(
			@PathVariable UUID uuid,
			@Valid @RequestBody CreateDayBlockRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.addBlock(uuid, request)));
	}

	@PutMapping("/schedule/blocks/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a day schedule block")
	public ResponseEntity<ApiResponse<DayBlockResponse>> updateBlock(
			@PathVariable UUID uuid,
			@Valid @RequestBody UpdateDayBlockRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateBlock(uuid, request)));
	}

	@DeleteMapping("/schedule/blocks/{uuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a day schedule block")
	public ResponseEntity<Void> deleteBlock(@PathVariable UUID uuid) {
		service.deleteBlock(uuid);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/schedule/day-templates/seed-defaults")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Seed default staggered recess/lunch templates per academic level")
	public ResponseEntity<ApiResponse<List<DayTemplateResponse>>> seedDefaults(
			@RequestParam UUID yearUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.seedDefaultTemplatesForYear(yearUuid)));
	}

	@PostMapping("/schedule/day-templates/{uuid}/apply-day-plan")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Apply day plan (entrada/salida/periodos) and regenerate recess/lunch",
			description = "Stores dayStart/dayEnd/periodMinutes, replaces RECESS/LUNCH blocks, "
					+ "keeps ASSEMBLY/GUIDANCE/SPECIALIST_RESERVED. ADR-SCH-12.")
	public ResponseEntity<ApiResponse<DayTemplateResponse>> applyDayPlan(
			@PathVariable UUID uuid,
			@Valid @RequestBody ApplyDayPlanRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.applyDayPlan(uuid, request)));
	}
}
