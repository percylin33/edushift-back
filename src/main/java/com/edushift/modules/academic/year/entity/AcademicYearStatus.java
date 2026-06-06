package com.edushift.modules.academic.year.entity;

/**
 * Lifecycle of an academic year inside a tenant.
 *
 * <h3>Transitions</h3>
 * <pre>
 *   PLANNING --(activate)--> ACTIVE --(close)--> CLOSED
 * </pre>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>At most ONE {@link #ACTIVE} year per tenant at a time. Enforced by
 *       a unique partial index ({@code uk_academic_years_tenant_active}).</li>
 *   <li>Activating a {@link #PLANNING} year while another is {@link #ACTIVE}
 *       transitions the previous one to {@link #CLOSED} in the same
 *       transaction.</li>
 *   <li>{@link #CLOSED} is read-only: editing or deleting a closed year is
 *       refused with {@code ACADEMIC_YEAR_LOCKED} (409). Re-opening would
 *       require a separate admin tool (out of Sprint 4 scope).</li>
 * </ul>
 *
 * <p>Matches the CHECK constraint {@code chk_academic_years_status} declared
 * in {@code V13__create_academic_years_table.sql}.</p>
 */
public enum AcademicYearStatus {

	/**
	 * Drafted but not yet effective. Editable. Does not unlock sections,
	 * periods, sessions or attendance.
	 */
	PLANNING,

	/**
	 * Currently in effect for the tenant. Maximum one per tenant.
	 * Modules downstream (sections, periods, teacher assignments,
	 * student enrollments) anchor on the ACTIVE year by default.
	 */
	ACTIVE,

	/**
	 * Archived. Historical reads are allowed; mutations are rejected with
	 * {@code ACADEMIC_YEAR_LOCKED}.
	 */
	CLOSED;

	public boolean isEditable() {
		return this != CLOSED;
	}

	public boolean isActivatable() {
		return this == PLANNING;
	}
}
