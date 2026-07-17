package com.edushift.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import java.util.EnumSet;
import java.util.List;
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
			new LmsRoleAuthorityMapper(mock(PermissionOverrideService.class));

	@Test
	@DisplayName("tenant_admin gets all LMS authorities (tasks + materials + quizzes + AI + payment-admin + announcements)")
	void tenantAdminHasAllAuthorities() {
		Set<String> auths = mapper.grantFor(UserRole.TENANT_ADMIN);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_CREATE,
				LmsAuthorities.LMS_TASK_GRADE,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_MATERIAL_WRITE,
				LmsAuthorities.LMS_MATERIAL_DELETE,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_CREATE,
				LmsAuthorities.LMS_QUIZ_GRADE,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_AI_GENERATE,
				LmsAuthorities.LMS_PAYMENT_ADMIN,
				// DEBT-FK-BUGS-2: announcements admin surface (BE-9.4).
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE);
	}

	@Test
	@DisplayName("teacher can read/create/grade tasks, manage materials, read/create/grade quizzes, generate AI (no submit)")
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
				LmsAuthorities.LMS_AI_GENERATE);
		assertThat(auths).doesNotContain(
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_PAYMENT_ADMIN);
	}

	@Test
	@DisplayName("student can read tasks + submit, read materials, read/submit quizzes (no create/grade)")
	void studentHasReadAndSubmit() {
		Set<String> auths = mapper.grantFor(UserRole.STUDENT);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT);
	}

	@Test
	@DisplayName("parent has the same set as student (submit on behalf)")
	void parentHasReadAndSubmit() {
		Set<String> auths = mapper.grantFor(UserRole.PARENT);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT);
	}

	@Test
	@DisplayName("staff is read-only: tasks + materials + quizzes + payment-admin")
	void staffIsReadOnly() {
		Set<String> auths = mapper.grantFor(UserRole.STAFF);
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_PAYMENT_ADMIN);
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
	@DisplayName("multiple roles → union, dedup'd")
	void unionIsDeduped() {
		// STUDENT and PARENT grant the same set; the result must not
		// duplicate any authority.
		Set<String> auths = mapper.mapAuthorities(
				EnumSet.of(UserRole.STUDENT, UserRole.PARENT));
		assertThat(auths).containsExactlyInAnyOrder(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT);
	}

	@Test
	@DisplayName("combined tenant_admin + teacher → still all 14 (admin has them all)")
	void tenantAdminAndTeacherStillHasAll() {
		// DEBT-FK-BUGS-2: +1 authority (LMS_ANNOUNCEMENTS_CREATE) added to
		// TENANT_ADMIN set, so the union goes from 13 to 14.
		Set<String> auths = mapper.mapAuthorities(
				EnumSet.of(UserRole.TENANT_ADMIN, UserRole.TEACHER));
		assertThat(auths).hasSize(14);
	}
}
