package com.edushift.modules.academic.course.service;

import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseLevelsRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Course} aggregate (Sprint 4 — BE-4.4).
 *
 * <h3>Filters for {@link #listCourses}</h3>
 * <ul>
 *   <li>{@code levelPublicUuid} — only courses linked to that level.</li>
 *   <li>{@code isActive} — only active or only inactive ({@code null} = both).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>course or level publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code COURSE_CODE_TAKEN}</td><td>409</td>
 *       <td>another course in the tenant already uses {@code code}
 *       (case-insensitive)</td></tr>
 *   <tr><td>{@code COURSE_NEEDS_AT_LEAST_ONE_LEVEL}</td><td>422</td>
 *       <td>create or replace-levels payload would leave the course
 *       without any associated level</td></tr>
 *   <tr><td>{@code COURSE_IN_USE_BY_ASSIGNMENTS}</td><td>409</td>
 *       <td><em>Reserved for BE-4.7</em>: teacher assignments will
 *       activate this code on delete when present.</td></tr>
 * </table>
 */
public interface CourseService {

	List<CourseListItem> listCourses(UUID levelPublicUuid, Boolean isActive);

	CourseResponse getCourse(UUID publicUuid);

	CourseResponse createCourse(CreateCourseRequest request);

	CourseResponse updateCourse(UUID publicUuid, UpdateCourseRequest request);

	CourseResponse replaceLevels(UUID publicUuid, UpdateCourseLevelsRequest request);

	void deleteCourse(UUID publicUuid);
}
