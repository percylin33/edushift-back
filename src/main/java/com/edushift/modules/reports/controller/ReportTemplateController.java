package com.edushift.modules.reports.controller;

import com.edushift.modules.reports.dto.ReportTemplateRequest;
import com.edushift.modules.reports.dto.ReportTemplateResponse;
import com.edushift.modules.reports.service.ReportTemplateService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the recurring-report-template catalog (Sprint
 * cierre-C / B12).
 *
 * <h3>Endpoints (under {@code /api/v1/reports/templates})</h3>
 * <table>
 *   <caption>Template endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                  </td><td>TENANT_ADMIN</td>
 *       <td>Page of {@link ReportTemplateResponse}</td></tr>
 *   <tr><td>GET   </td><td>/{uuid}            </td><td>TENANT_ADMIN</td>
 *       <td>{@link ReportTemplateResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                  </td><td>TENANT_ADMIN</td>
 *       <td>{@link ReportTemplateResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{uuid}            </td><td>TENANT_ADMIN</td>
 *       <td>{@link ReportTemplateResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{uuid}            </td><td>TENANT_ADMIN</td>
 *       <td>204 (soft-delete)</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/reports/templates")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "Report Templates",
		description = "Tenant-defined recurring report templates with cron + email (Sprint cierre-C / B12)")
@RequiredArgsConstructor
public class ReportTemplateController {

	private final ReportTemplateService service;

	@GetMapping
	@Operation(summary = "List tenant report templates (paginated)")
	public ApiResponse<Page<ReportTemplateResponse>> list(Pageable pageable) {
		return ApiResponse.ok(service.list(pageable));
	}

	@GetMapping("/{publicUuid}")
	@Operation(summary = "Get a template")
	public ApiResponse<ReportTemplateResponse> get(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(service.get(publicUuid));
	}

	@PostMapping
	@Operation(summary = "Create a template. Validates the cron expression; "
			+ "returns 400 REPORT_TEMPLATE_BAD_CRON on parse failure.")
	public ApiResponse<ReportTemplateResponse> create(@Valid @RequestBody ReportTemplateRequest req) {
		return ApiResponse.ok(service.create(req));
	}

	@PutMapping("/{publicUuid}")
	@Operation(summary = "Replace a template. Recomputes next_run_at.")
	public ApiResponse<ReportTemplateResponse> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody ReportTemplateRequest req) {
		return ApiResponse.ok(service.update(publicUuid, req));
	}

	@DeleteMapping("/{publicUuid}")
	@Operation(summary = "Soft-delete a template. Preserves audit history.")
	public ApiResponse<Void> delete(@PathVariable UUID publicUuid) {
		service.softDelete(publicUuid);
		return ApiResponse.ok(null);
	}
}