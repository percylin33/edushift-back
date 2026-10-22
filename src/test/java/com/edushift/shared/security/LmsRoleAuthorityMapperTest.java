package com.edushift.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.dto.RolePermissionOverrideDto;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LmsRoleAuthorityMapper}
 * (Sprint 7a / BE-7a.3, extended in BE-7b.0 with the
 * {@code LMS_QUIZ_*} authorities, extended in BE-7c.1 with
 * {@code LMS_AI_GENERATE}). Covers the 5 roles × 12 LMS
 * authorities matrix plus a few defensive cases (null input,
 * unknown role, union of multiple roles).
 */
class LmsRoleAuthorityMapperTest {

	private final LmsRoleAuthorityMapper mapper =
			new LmsRoleAuthorityMapper(overrides(Map.of(), Map.of()));

	@Test
	@DisplayName("tenant_admin can inspect, review and publish annual disclosures but not prepare or submit")
	void tenantAdminCanReviewDisclosuresWithoutPreparingOrSubmitting() {
		Set<String> auths = mapper.grantFor(UserRole.TENANT_ADMIN);
		assertThat(auths).contains(
				LmsAuthorities.LMS_DISCLOSURE_READ,
				LmsAuthorities.LMS_DISCLOSURE_REVIEW,
				LmsAuthorities.LMS_DISCLOSURE_PUBLISH);
		assertThat(auths).doesNotContain(
				LmsAuthorities.LMS_DISCLOSURE_MANAGE,
				LmsAuthorities.LMS_DISCLOSURE_SUBMIT);
	}

	@Test
	@DisplayName("teacher permissions include classroom AI but no learner submission")
	void teacherHasExpectedAuthorities() {
		Set<String> auths = mapper.grantFor(UserRole.TEACHER);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_CREATE,
				LmsAuthorities.LMS_TASK_GRADE,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_MATERIAL_WRITE,
				LmsAuthorities.LMS_MATERIAL_DELETE,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_CREATE,
				LmsAuthorities.LMS_QUIZ_GRADE,
				LmsAuthorities.LMS_AI_GENERATE,
				LmsAuthorities.AI_TEACHER_USE,
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE_OWN);
		assertThat(auths).doesNotContain(
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_PAYMENT_ADMIN);
	}

	@Test
	@DisplayName("student permissions include learner workspace and tutor access")
	void studentHasReadAndSubmit() {
		Set<String> auths = mapper.grantFor(UserRole.STUDENT);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.ME_READ,
				LmsAuthorities.ME_ATTENDANCE_READ,
				LmsAuthorities.ME_ATTENDANCE_JUSTIFY,
				LmsAuthorities.ME_QR_READ,
				LmsAuthorities.ME_PAYMENTS_READ,
				LmsAuthorities.ME_PAYMENTS_CHECKOUT,
				LmsAuthorities.AI_TUTOR_USE);
	}

	@Test
	@DisplayName("parent permissions include family access and family AI")
	void parentHasReadAndSubmit() {
		Set<String> auths = mapper.grantFor(UserRole.PARENT);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_FAMILY_READ,
				LmsAuthorities.LMS_ATTENDANCE_JUSTIFY,
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.AI_FAMILY_USE);
	}

	@Test
	@DisplayName("staff can read, prepare and submit annual disclosures")
	void staffCanPrepareAndSubmitAnnualDisclosures() {
		Set<String> auths = mapper.grantFor(UserRole.STAFF);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_PAYMENT_ADMIN,
				LmsAuthorities.LMS_DISCLOSURE_READ,
				LmsAuthorities.LMS_DISCLOSURE_MANAGE,
				LmsAuthorities.LMS_DISCLOSURE_SUBMIT,
				LmsAuthorities.USERS_PROVISION_SECTION);
	}

	@Test
	@DisplayName("null input → empty set (no NPE)")
	void nullRolesReturnEmpty() {
		assertThat(mapper.mapAuthorities(null)).isEmpty();
	}

	@Test
	@DisplayName("empty input → empty set")
	void emptyRolesReturnEmpty() {
		assertThat(mapper.mapAuthorities(List.of())).isEmpty();
	}

	@Test
	@DisplayName("multiple roles → union, deduplicating overlap")
	void unionIsDeduped() {
		// Student and parent share classroom permissions while each
		// retains role-specific workspace and family permissions.
		Set<String> auths = mapper.mapAuthorities(
				EnumSet.of(UserRole.STUDENT, UserRole.PARENT));
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_FAMILY_READ,
				LmsAuthorities.LMS_ATTENDANCE_JUSTIFY,
				LmsAuthorities.ME_READ,
				LmsAuthorities.ME_ATTENDANCE_READ,
				LmsAuthorities.ME_ATTENDANCE_JUSTIFY,
				LmsAuthorities.ME_QR_READ,
				LmsAuthorities.ME_PAYMENTS_READ,
				LmsAuthorities.ME_PAYMENTS_CHECKOUT,
				LmsAuthorities.AI_TUTOR_USE,
				LmsAuthorities.AI_FAMILY_USE);
	}

	@Test
	@DisplayName("combined tenant_admin + teacher keeps all admin authorities")
	void tenantAdminAndTeacherStillHasAll() {
		Set<String> auths = mapper.mapAuthorities(
				EnumSet.of(UserRole.TENANT_ADMIN, UserRole.TEACHER));
		assertThat(auths)
				.containsAll(mapper.grantFor(UserRole.TENANT_ADMIN))
				.containsAll(mapper.grantFor(UserRole.TEACHER));
	}

	@Test
	@DisplayName("a tenant override cannot grant announcement publishing to a student")
	void studentOverrideCannotPublishAnnouncements() {
		PermissionOverrideService tenantOverrides = overrides(
				Map.of(UserRole.STUDENT,
						Set.of(LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE,
								LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE_OWN)),
				Map.of());
		LmsRoleAuthorityMapper scoped = new LmsRoleAuthorityMapper(tenantOverrides);
		Set<String> auths = scoped.resolveFor(java.util.UUID.randomUUID(), List.of(UserRole.STUDENT));
		assertThat(auths).doesNotContain(
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE,
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE_OWN);
	}

	private static PermissionOverrideService overrides(
			Map<UserRole, Set<String>> grants,
			Map<UserRole, Set<String>> revocations) {
		return new PermissionOverrideService() {
			@Override
			public Map<UserRole, Set<String>> snapshotFor(java.util.UUID tenantId) {
				return grants;
			}

			@Override
			public Map<UserRole, Set<String>> revokedFor(java.util.UUID tenantId) {
				return revocations;
			}

			@Override
			public List<RolePermissionOverrideDto> listAll(java.util.UUID tenantId) {
				return List.of();
			}

			@Override
			public RolePermissionOverrideDto upsert(
					java.util.UUID tenantId, UserRole role, String authority, boolean granted, java.util.UUID actorUserId) {
				throw new UnsupportedOperationException("Test override is read-only");
			}

			@Override
			public void evict(java.util.UUID tenantId) {
				// The mapper only reads this fixture.
			}
		};
	}
}
