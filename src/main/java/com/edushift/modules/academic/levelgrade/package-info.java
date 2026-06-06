/**
 * <strong>academic.levelgrade sub-module</strong> — coarse education
 * stages ({@code AcademicLevel}) and their grade progressions
 * ({@code Grade}). Sprint 4 / BE-4.2.
 *
 * <p>Both aggregates are tenant-aware and seeded automatically on tenant
 * signup (defaults {@code INICIAL/PRIMARIA/SECUNDARIA} from
 * {@code AcademicDefaults}). The seed is invoked from
 * {@code TenantServiceImpl.register} after the admin user is persisted,
 * inside the same {@code TenantContext.runAs} transaction.</p>
 *
 * <p>Downstream sub-modules:</p>
 * <ul>
 *   <li>{@code academic.sections} (BE-4.3) — sections reference a grade.</li>
 *   <li>{@code academic.courses} (BE-4.4) — courses are linked to levels (M:N).</li>
 * </ul>
 */
package com.edushift.modules.academic.levelgrade;
