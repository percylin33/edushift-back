package com.edushift.modules.academic.course.controller;

import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseLevelsRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import com.edushift.modules.academic.course.service.CourseService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Course} aggregate (Sprint 4 — BE-4.4).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/courses})</h3>
 * <table>
 *   <caption>Course endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                            </td>
 *       <td>TENANT_ADMIN</td><td>{@code List<}{@link CourseListItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}                </td>
 *       <td>TENANT_ADMIN</td><td>{@link CourseResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                            </td>
 *       <td>TENANT_ADMIN</td><td>{@link CourseResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}                </td>
 *       <td>TENANT_ADMIN</td><td>{@link CourseResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/levels         </td>
 *       <td>TENANT_ADMIN</td><td>{@link CourseResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}                </td>
 *       <td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/academic/courses")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Courses",
		description = "Course catalog (M:N with levels) per tenant (Sprint 4 — BE-4.4)")
public class CourseController {

	private final CourseService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List courses (TENANT_ADMIN)",
			description = "Sorted by name asc. Optional ?levelId narrows to courses linked "
					+ "to that level. Optional ?isActive=true|false narrows by activation flag."
	)
	public ResponseEntity<List<CourseListItem>> list(
			@Parameter(description = "Filter by level publicUuid (only courses linked to it)")
			@RequestParam(name = "levelId", required = false) UUID levelPublicUuid,
			@Parameter(description = "Filter by activation flag")
			@RequestParam(name = "isActive", required = false) Boolean isActive
	) {
		return ResponseEntity.ok(service.listCourses(levelPublicUuid, isActive));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a course with its levels (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<CourseResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getCourse(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a course (TENANT_ADMIN)",
			description = "Body must include >= 1 level publicUuid. "
					+ "409 COURSE_CODE_TAKEN on case-insensitive code collision. "
					+ "404 RESOURCE_NOT_FOUND if any level publicUuid is unknown "
					+ "(includes cross-tenant). 422 COURSE_NEEDS_AT_LEAST_ONE_LEVEL "
					+ "if the resolved level set is empty."
	)
	public ResponseEntity<ApiResponse<CourseResponse>> create(
			@Valid @RequestBody CreateCourseRequest request
	) {
		CourseResponse response = service.createCourse(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a course (TENANT_ADMIN)",
			description = "Partial-merge. Use POST /levels to change level associations. "
					+ "409 COURSE_CODE_TAKEN on case-insensitive collision."
	)
	public ResponseEntity<ApiResponse<CourseResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateCourseRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateCourse(publicUuid, request)));
	}

	@PostMapping("/{publicUuid}/levels")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Replace the level set of a course (TENANT_ADMIN)",
			description = "Replace semantics: the payload overwrites the current level "
					+ "set. The diff is applied minimally (only added/removed rows are "
					+ "written). 422 COURSE_NEEDS_AT_LEAST_ONE_LEVEL on empty payload. "
					+ "404 RESOURCE_NOT_FOUND on unknown / cross-tenant level publicUuid."
	)
	public ResponseEntity<ApiResponse<CourseResponse>> replaceLevels(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateCourseLevelsRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.replaceLevels(publicUuid, request)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a course (TENANT_ADMIN)",
			description = "Cascades soft-delete to its course_levels rows. BE-4.7 will "
					+ "add 409 COURSE_IN_USE_BY_ASSIGNMENTS when teacher assignments wire in. "
					+ "Prefer setting isActive=false to hide a course from FE without losing "
					+ "history."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteCourse(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
