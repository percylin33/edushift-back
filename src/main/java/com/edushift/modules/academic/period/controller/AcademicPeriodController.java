package com.edushift.modules.academic.period.controller;

import com.edushift.modules.academic.period.dto.AcademicPeriodListItem;
import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.dto.UpdateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.service.AcademicPeriodService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code AcademicPeriod} aggregate
 * (Sprint 4 — BE-4.5).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/periods})</h3>
 * All endpoints require {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/academic/periods")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Periods",
		description = "Academic periods (BIMESTRE/TRIMESTRE/ANUAL) per year (Sprint 4 / BE-4.5)")
public class AcademicPeriodController {

	private final AcademicPeriodService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List periods (TENANT_ADMIN)",
			description = "If ?academicYearId is omitted, falls back to the active year of "
					+ "the tenant; if there is no active year, returns an empty list. "
					+ "Sorted by (period_type, ordinal) asc."
	)
	public ResponseEntity<List<AcademicPeriodListItem>> list(
			@Parameter(description = "Filter by academic year publicUuid (default = active year)")
			@RequestParam(name = "academicYearId", required = false) UUID academicYearPublicUuid,
			@Parameter(description = "Filter by period type")
			@RequestParam(name = "periodType", required = false) PeriodType periodType
	) {
		return ResponseEntity.ok(service.listPeriods(academicYearPublicUuid, periodType));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a single period (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<AcademicPeriodResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getPeriod(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a period (TENANT_ADMIN)",
			description = "If `name` is omitted it is auto-generated as "
					+ "`<roman_ordinal> <PeriodType>` (e.g. `II Bimestre`). "
					+ "Validation order: ACADEMIC_YEAR_LOCKED → PERIOD_DATE_INVERTED "
					+ "→ PERIOD_OUT_OF_YEAR_RANGE → PERIOD_ORDINAL_TAKEN | "
					+ "PERIOD_ORDINAL_GAP → PERIOD_DATE_OVERLAP."
	)
	public ResponseEntity<ApiResponse<AcademicPeriodResponse>> create(
			@Valid @RequestBody CreateAcademicPeriodRequest request
	) {
		AcademicPeriodResponse response = service.createPeriod(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a period (TENANT_ADMIN)",
			description = "Partial-merge over `name`, `startDate`, `endDate`. The "
					+ "`(year, type, ordinal)` triple is intentionally immutable — "
					+ "delete and recreate to renumber."
	)
	public ResponseEntity<ApiResponse<AcademicPeriodResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateAcademicPeriodRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updatePeriod(publicUuid, request)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a period (TENANT_ADMIN)",
			description = "Only the highest ordinal of a `(year, type)` can be deleted "
					+ "(409 PERIOD_NOT_LAST_ORDINAL otherwise) to preserve contiguity. "
					+ "BE-4.7 will add 409 PERIOD_IN_USE_BY_ASSIGNMENTS."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deletePeriod(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
