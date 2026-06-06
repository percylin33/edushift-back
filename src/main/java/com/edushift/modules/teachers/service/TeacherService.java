package com.edushift.modules.teachers.service;

import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.InviteTeacherResponse;
import com.edushift.modules.teachers.dto.LinkTeacherUserRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public surface of the {@code Teacher} aggregate (Sprint 4 — BE-4.6).
 *
 * <h3>Filters for {@link #listTeachers}</h3>
 * <ul>
 *   <li>{@code search} — case-insensitive substring on names, document
 *       and email.</li>
 *   <li>{@code employmentStatus} — exact match.</li>
 *   <li>{@code hasUserAccount} — narrow to teachers with/without a
 *       linked User.</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>teacher or user publicUuid unknown for the tenant
 *           (anti-enumeration covers cross-tenant)</td></tr>
 *   <tr><td>{@code TEACHER_DOCUMENT_TAKEN}</td><td>409</td>
 *       <td>{@code (documentType, documentNumber)} already in use</td></tr>
 *   <tr><td>{@code TEACHER_EMAIL_TAKEN}</td><td>409</td>
 *       <td>email already in use by another teacher in the tenant</td></tr>
 *   <tr><td>{@code TEACHER_ALREADY_HAS_USER}</td><td>409</td>
 *       <td>link-user / invite refused because teacher.userId is set</td></tr>
 *   <tr><td>{@code USER_NOT_TEACHER_ROLE}</td><td>409</td>
 *       <td>link-user with a user that lacks the {@code TEACHER} role</td></tr>
 *   <tr><td>{@code USER_ALREADY_LINKED_TO_TEACHER}</td><td>409</td>
 *       <td>link-user with a user that is already linked to another teacher</td></tr>
 *   <tr><td>{@code TEACHER_NEEDS_EMAIL_TO_INVITE}</td><td>422</td>
 *       <td>invite called on a teacher with no {@code email}</td></tr>
 *   <tr><td>{@code TEACHER_HAS_ACTIVE_ASSIGNMENTS}</td><td>409</td>
 *       <td><em>Reserved for BE-4.7</em> — delete refused while the
 *           teacher has active assignments.</td></tr>
 * </table>
 */
public interface TeacherService {

	Page<TeacherListItem> listTeachers(String search, EmploymentStatus employmentStatus,
			Boolean hasUserAccount, Pageable pageable);

	TeacherResponse getTeacher(UUID publicUuid);

	TeacherResponse createTeacher(CreateTeacherRequest request);

	TeacherResponse updateTeacher(UUID publicUuid, UpdateTeacherRequest request);

	TeacherResponse linkUser(UUID publicUuid, LinkTeacherUserRequest request);

	InviteTeacherResponse invite(UUID publicUuid);

	void deleteTeacher(UUID publicUuid);
}
