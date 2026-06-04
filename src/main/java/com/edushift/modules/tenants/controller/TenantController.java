package com.edushift.modules.tenants.controller;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code tenants} module.
 *
 * <h3>Endpoints (under {@code /api/v1/tenants})</h3>
 * <table>
 *   <caption>Tenant endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET  </td><td>/by-slug/{slug}</td><td>—</td>           <td>{@link ApiResponse}&lt;{@link TenantSummary}&gt;</td></tr>
 *   <tr><td>GET  </td><td>/me              </td><td>required</td>  <td>{@link ApiResponse}&lt;{@link TenantResponse}&gt;</td></tr>
 *   <tr><td>PATCH</td><td>/me              </td><td>TENANT_ADMIN<sup>*</sup></td><td>{@link ApiResponse}&lt;{@link TenantResponse}&gt;</td></tr>
 *   <tr><td>POST </td><td>/register        </td><td>—</td>          <td>{@link AuthResponse} (raw, OAuth-shape)</td></tr>
 * </table>
 *
 * <p><sup>*</sup> Role enforcement lands with {@code BE-2.4} (JWT
 * carries the role claim and {@code @PreAuthorize("hasRole('TENANT_ADMIN')")}
 * activates). Until then the endpoint requires only authentication —
 * acceptable risk because the only authenticated user in dev is the
 * tenant admin himself, and tests will assert the role gate when it
 * lands.
 *
 * <h3>Why the public path is {@code /by-slug/{slug}} and not {@code /{slug}}</h3>
 * Two reasons:
 * <ul>
 *   <li>Matches the verb intent: the public lookup is a search by
 *       known slug, not "fetch by id". {@code /by-slug} reads
 *       symmetrically with the planned {@code /by-domain/{host}}.</li>
 *   <li>Sidesteps a routing pitfall: {@code /{slug}} would shadow
 *       any future static path under {@code /tenants/} (e.g.
 *       {@code /tenants/limits}). Reserving {@code /tenants/me} +
 *       {@code /tenants/by-slug/...} keeps the URL grammar
 *       expandable.</li>
 * </ul>
 *
 * <h3>Response envelopes</h3>
 * Every endpoint here returns the project-standard
 * {@link ApiResponse}{@code <T>}. The auth controller's "raw token
 * response" exception ({@code AuthResponse} returned bare) is specific
 * to OAuth-flavored endpoints and does not apply to tenant CRUD.
 */
@RestController
@RequestMapping("/tenants")
@Validated
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant catalog: lookup, current tenant, partial updates")
public class TenantController {

	private final TenantService tenantService;

	@GetMapping("/by-slug/{slug}")
	@Operation(
			summary = "Resolve a tenant by slug (public)",
			description = "Returns a public-safe summary (name, branding, status) so the "
					+ "frontend can theme the login screen before any user authenticates. "
					+ "No sensitive fields (plan, settings, feature flags) are exposed here."
	)
	public ResponseEntity<ApiResponse<TenantSummary>> getBySlug(
			@PathVariable
			@NotBlank(message = "slug is required")
			@Size(min = 2, max = 80, message = "slug length out of range")
			@Parameter(description = "Case-insensitive slug", example = "demo")
			String slug
	) {
		TenantSummary summary = tenantService.findBySlug(slug);
		return ResponseEntity.ok(ApiResponse.ok(summary));
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(
			summary = "Get the current tenant (authenticated)",
			description = "Returns the full tenant record bound to the bearer's "
					+ "{@code tenant_id} claim. Includes plan, branding, settings, "
					+ "feature flags and capacity caps."
	)
	public ResponseEntity<ApiResponse<TenantResponse>> getCurrent() {
		TenantResponse response = tenantService.findCurrent();
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PatchMapping("/me")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Partial update of the current tenant (TENANT_ADMIN)",
			description = "Field-by-field merge. Null fields in the request body "
					+ "are interpreted as 'leave unchanged'. Slug, status, and plan "
					+ "are not editable here — they have dedicated lifecycle endpoints. "
					+ "Requires the TENANT_ADMIN role."
	)
	public ResponseEntity<ApiResponse<TenantResponse>> updateCurrent(
			@Valid @RequestBody UpdateTenantRequest request
	) {
		TenantResponse response = tenantService.updateCurrent(request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	/**
	 * Onboarding's last lifecycle step: PENDING → ACTIVE. Idempotent on
	 * already-ACTIVE tenants (returns 200 with the current snapshot).
	 * Refusing other source statuses raises 409 {@code TENANT_NOT_ACTIVATABLE}
	 * so the SPA can route the user to support instead of silently
	 * pretending the call worked.
	 */
	@PostMapping("/me/activate")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Activate the current tenant (TENANT_ADMIN)",
			description = "Promotes a PENDING tenant to ACTIVE. Idempotent on "
					+ "ACTIVE tenants. Refuses SUSPENDED / INACTIVE source "
					+ "statuses with 409 TENANT_NOT_ACTIVATABLE."
	)
	public ResponseEntity<ApiResponse<TenantResponse>> activateCurrent() {
		TenantResponse response = tenantService.activateCurrent();
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	/**
	 * Public self-signup. Mirrors the response shape of {@code /auth/login}
	 * (raw {@link AuthResponse}, no envelope) so the SPA can hand the
	 * payload straight to its session store without unwrapping. The HTTP
	 * status is {@code 201 Created} because we do create a new resource
	 * (the tenant) — most public-facing token endpoints return 200, but
	 * 201 is more accurate here and includes a richer response semantics
	 * for the front to react to.
	 */
	@PostMapping("/register")
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Register a new tenant + admin (public self-signup)",
			description = "Creates the tenant in PENDING/TRIAL state, the admin "
					+ "user, and returns a logged-in session ready for the "
					+ "onboarding wizard. 409 TENANT_SLUG_TAKEN when the slug "
					+ "collides with an existing tenant."
	)
	public ResponseEntity<AuthResponse> register(
			@Valid @RequestBody RegisterTenantRequest request
	) {
		AuthResponse session = tenantService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(session);
	}

}
