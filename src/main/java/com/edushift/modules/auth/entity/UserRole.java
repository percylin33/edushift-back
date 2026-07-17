package com.edushift.modules.auth.entity;

/**
 * Role assigned to a {@link User} for authorization purposes.
 *
 * <h3>Storage</h3>
 * Persisted as plain strings inside {@code users.roles} (a Postgres
 * {@code varchar[]}), surfaced as the {@code roles} JWT claim, and
 * consumed by Spring Security as
 * {@code SimpleGrantedAuthority("ROLE_" + name())} so that
 * {@code @PreAuthorize("hasRole('TENANT_ADMIN')")} and friends just work.
 *
 * <h3>Why an enum and not a table</h3>
 * Sprint 2 only needs to gate endpoints on a small, closed set of roles
 * known at deploy time. A relational catalog would force every API
 * call to round-trip the DB just to map name → id; a server-side enum
 * is faster and simpler. When Sprint 3 needs custom permissions per
 * role (CRUD on students, viewing grades, etc.) we promote roles to a
 * table and keep this enum as the canonical "role identifier" — the
 * JWT shape doesn't change.
 *
 * <h3>Naming convention</h3>
 * Stored verbatim ({@code TENANT_ADMIN}, not {@code ROLE_TENANT_ADMIN}).
 * The {@code "ROLE_"} prefix is added at the Spring Security boundary
 * by {@code JwtAuthenticationFilter} — keeping it out of the data
 * lets the same string flow through the JWT, the DB, and tooling
 * without prefix juggling.
 */
public enum UserRole {

	/** Owns the tenant: full CRUD on tenant settings, users, billing. */
	TENANT_ADMIN,

	/** Teaches one or more groups; reads/writes academic records they own. */
	TEACHER,

	/** Enrolled student; reads their own grades, schedule, attendance. */
	STUDENT,

	/** Parent / guardian; reads records of linked students. */
	PARENT,

	/** Non-teaching staff (admin assistant, finance, IT). Granular perms TBD. */
	STAFF,

	/**
	 * Platform super-administrator. Cross-tenant role that owns the EduShift
	 * deployment: manages all tenants, plans, subscriptions, billing, and
	 * can impersonate any user for support. Bound to the sentinel tenant
	 * {@code edushift-system} (UUID {@code 00000000-0000-0000-0000-000000000001}).
	 * Sprint 15 / BE-15.1.
	 */
	SUPER_ADMIN;

	/** Convenience: parse a string back to the enum, returning null on miss. */
	public static UserRole fromName(String name) {
		if (name == null) return null;
		for (UserRole r : values()) {
			if (r.name().equals(name)) return r;
		}
		return null;
	}

}
