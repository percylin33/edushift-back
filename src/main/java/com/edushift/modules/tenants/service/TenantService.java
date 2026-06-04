package com.edushift.modules.tenants.service;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;

/**
 * Tenant catalog operations exposed to controllers.
 *
 * <h3>Errors (mapped by GlobalExceptionHandler)</h3>
 * <ul>
 *   <li>{@code TenantNotFoundException} → 404 {@code TENANT_NOT_FOUND}</li>
 *   <li>{@code ConflictException}       → 409, with code:
 *     <ul>
 *       <li>{@code TENANT_SLUG_TAKEN}     — registration slug collides</li>
 *       <li>{@code TENANT_DOMAIN_TAKEN}   — customDomain collides on update</li>
 *     </ul>
 *   </li>
 *   <li>{@code UnauthorizedException}   → 401 {@code MISSING_TENANT_CONTEXT}
 *       — internal invariant violation; the JWT filter should have bound a
 *       tenant before this service runs. If you see it in production, the
 *       chain is misconfigured.</li>
 * </ul>
 */
public interface TenantService {

	/**
	 * Public lookup by slug. Returns the {@link TenantSummary} (no
	 * sensitive fields) so the front can render a tenant-aware login
	 * screen even before the user authenticates.
	 *
	 * @param slug case-insensitive slug
	 * @throws com.edushift.modules.tenants.exception.TenantNotFoundException
	 *         when no non-deleted tenant matches the slug
	 */
	TenantSummary findBySlug(String slug);

	/**
	 * Returns the full record of the tenant attached to the current
	 * security / tenant context. Used by {@code GET /v1/tenants/me}.
	 *
	 * @throws com.edushift.shared.exception.UnauthorizedException
	 *         when no tenant is bound in the call's context
	 */
	TenantResponse findCurrent();

	/**
	 * Applies a partial update to the current tenant and returns the
	 * fresh state. See {@link UpdateTenantRequest} for the per-field
	 * merge semantics.
	 *
	 * <h3>Concurrency</h3>
	 * The update runs in a transaction managed by the implementation;
	 * Hibernate's dirty checking persists modifications on commit.
	 * Two concurrent PATCHes on the same tenant rely on PostgreSQL's
	 * row-level locking under {@code REPEATABLE READ} (the default
	 * isolation here is {@code READ COMMITTED}, sufficient because
	 * each field is overwritten atomically; we don't read-modify-write
	 * jsonb keys outside the transaction).
	 */
	TenantResponse updateCurrent(UpdateTenantRequest patch);

	/**
	 * Self-signup: create a new tenant + admin user atomically and return
	 * a logged-in session for the admin.
	 *
	 * <h3>Flow</h3>
	 * <ol>
	 *   <li>Reject duplicate slugs early ({@code 409 TENANT_SLUG_TAKEN}).</li>
	 *   <li>Insert the {@code tenants} row in {@code PENDING / TRIAL}.</li>
	 *   <li>Bind {@link com.edushift.shared.multitenancy.TenantContext} to
	 *       the new id, then create the admin {@code users} row inside
	 *       that scope.</li>
	 *   <li>Issue a token pair via
	 *       {@link com.edushift.modules.auth.service.AuthService#issueSession}
	 *       so the SPA jumps from the registration form into the
	 *       onboarding wizard without a second login round-trip.</li>
	 * </ol>
	 *
	 * <h3>Atomicity</h3>
	 * The whole sequence runs in a single transaction managed via
	 * {@link org.springframework.transaction.support.TransactionTemplate}
	 * inside a {@code TenantContext.runAs} block — same pattern as
	 * {@code AuthService.login} (see that class's javadoc for why
	 * {@code @Transactional} on the public method is wrong here).
	 *
	 * @return {@link AuthResponse} with bearer + refresh tokens for the
	 *         freshly-created admin
	 */
	AuthResponse register(RegisterTenantRequest request);

	/**
	 * Promote the current tenant from {@code PENDING} to {@code ACTIVE}.
	 * Called by the onboarding wizard's last step ({@code complete}) once
	 * the admin has supplied the institution profile.
	 *
	 * <h3>Why this lives in a dedicated lifecycle endpoint</h3>
	 * {@link #updateCurrent} is intentionally restricted to "data" fields
	 * ({@code name}, {@code branding}, {@code settings}, …); status
	 * transitions belong to a stricter contract:
	 * <ul>
	 *   <li>{@code PENDING → ACTIVE}: idempotent for our purposes — calling
	 *       activate twice on a tenant that is already {@code ACTIVE}
	 *       returns the current state without raising.</li>
	 *   <li>Other transitions (suspend, archive, delete) will land on
	 *       sibling endpoints with their own auth checks.</li>
	 * </ul>
	 *
	 * <h3>Idempotency contract</h3>
	 * Re-issuing the activation on an already-ACTIVE tenant is a no-op
	 * (returns the current snapshot). Activation against {@code SUSPENDED}
	 * or {@code INACTIVE} raises {@code TENANT_NOT_ACTIVATABLE} (409) — a
	 * suspended tenant must be reinstated through admin tooling, not the
	 * onboarding wizard.
	 *
	 * @return the freshly persisted {@link TenantResponse}
	 */
	TenantResponse activateCurrent();

}
