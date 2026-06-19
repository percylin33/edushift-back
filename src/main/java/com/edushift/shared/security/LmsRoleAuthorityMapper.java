package com.edushift.shared.security;

import com.edushift.modules.auth.entity.UserRole;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps a set of coarse user roles to a set of granular
 * {@code LMS_*} authorities (Sprint 7a / BE-7a.3).
 *
 * <h3>Mapping matrix (per the BE-7a.3 spec, extended in BE-7b.0 and BE-7c.1)</h3>
 * <table>
 *   <caption>Role → LMS authorities</caption>
 *   <tr><th>Role</th><th>Authorities granted</th></tr>
 *   <tr><td>{@link UserRole#TENANT_ADMIN TENANT_ADMIN}</td>
 *       <td>all 11 LMS authorities (tasks + materials + quizzes + AI)</td></tr>
 *   <tr><td>{@link UserRole#TEACHER TEACHER}</td>
 *       <td>LMS_TASK_READ, LMS_TASK_CREATE, LMS_TASK_GRADE,
 *           LMS_MATERIAL_READ, LMS_MATERIAL_WRITE,
 *           LMS_MATERIAL_DELETE,
 *           LMS_QUIZ_READ, LMS_QUIZ_CREATE, LMS_QUIZ_GRADE,
 *           LMS_AI_GENERATE</td></tr>
 *   <tr><td>{@link UserRole#STUDENT STUDENT}</td>
 *       <td>LMS_TASK_READ, LMS_TASK_SUBMIT, LMS_MATERIAL_READ,
 *           LMS_QUIZ_READ, LMS_QUIZ_SUBMIT</td></tr>
 *   <tr><td>{@link UserRole#PARENT PARENT}</td>
 *       <td>LMS_TASK_READ, LMS_TASK_SUBMIT,
 *           LMS_MATERIAL_READ, LMS_QUIZ_READ, LMS_QUIZ_SUBMIT</td></tr>
 *   <tr><td>{@link UserRole#STAFF STAFF}</td>
 *       <td>LMS_TASK_READ, LMS_MATERIAL_READ, LMS_QUIZ_READ (read-only)</td></tr>
 * </table>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>The mapper is a stateless {@code @Component} so it can be
 *       injected by the JWT filter, the {@code @PreAuthorize}
 *       SpEL evaluator (if we ever need it), and unit tests.</li>
 *   <li>Unknown / null roles are silently ignored: defensive against
 *       forward-compat role names that the JWT carries but the
 *       server has not been updated for. The mapper never throws.</li>
 *   <li>Output order is deterministic (LinkedHashSet) so test
 *       assertions on the set are stable.</li>
 *   <li>Authority strings are returned <em>without</em> the
 *       {@code ROLE_} prefix because they are consumed by
 *       {@code hasAuthority(...)} (not {@code hasRole(...)}).
 *       {@link LmsAuthorities} defines them in this shape.</li>
 * </ul>
 */
@Component
public class LmsRoleAuthorityMapper {

	/**
	 * @param roles coarse user roles (e.g. from the JWT's
	 *              {@code roles} claim). May contain unknown values.
	 * @return the union of {@code LMS_*} authorities granted by the
	 *         roles, dedup'd, in declaration order. Empty when the
	 *         input is null or no role grants any authority.
	 */
	public Set<String> mapAuthorities(Collection<UserRole> roles) {
		if (roles == null || roles.isEmpty()) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (UserRole role : roles) {
			if (role != null) {
				out.addAll(grantFor(role));
			}
		}
		return out;
	}

	/**
	 * Single-role variant. Useful for tests and for documenting the
	 * matrix in one place.
	 */
	Set<String> grantFor(UserRole role) {
		return switch (role) {
		case TENANT_ADMIN -> Set.of(
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
				LmsAuthorities.LMS_AI_GENERATE);
		case TEACHER -> Set.of(
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
			case STUDENT -> Set.of(
					LmsAuthorities.LMS_TASK_READ,
					LmsAuthorities.LMS_TASK_SUBMIT,
					LmsAuthorities.LMS_MATERIAL_READ,
					LmsAuthorities.LMS_QUIZ_READ,
					LmsAuthorities.LMS_QUIZ_SUBMIT);
			case PARENT -> Set.of(
					LmsAuthorities.LMS_TASK_READ,
					LmsAuthorities.LMS_TASK_SUBMIT,
					LmsAuthorities.LMS_MATERIAL_READ,
					LmsAuthorities.LMS_QUIZ_READ,
					LmsAuthorities.LMS_QUIZ_SUBMIT);
			case STAFF -> Set.of(
					LmsAuthorities.LMS_TASK_READ,
					LmsAuthorities.LMS_MATERIAL_READ,
					LmsAuthorities.LMS_QUIZ_READ);
		};
	}
}
