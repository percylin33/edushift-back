package com.edushift.modules.academic.course.service.impl;

import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseLevelsRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.mapper.CourseMapper;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.course.service.CourseService;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CourseService}.
 *
 * <h3>Replace-levels strategy</h3>
 * The pivot is treated as a set: when the client sends new levels we
 * (1) compute the diff against the current set, (2) soft-delete the
 * removals, and (3) insert the additions. We deliberately avoid a
 * "delete-all + reinsert" approach to keep audit history clean and to
 * avoid bumping {@code updated_at} on rows that did not actually change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

	private final CourseRepository courseRepository;
	private final CourseLevelRepository courseLevelRepository;
	private final AcademicLevelRepository levelRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final CourseMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<CourseListItem> listCourses(UUID levelPublicUuid, Boolean isActive) {
		List<Course> courses;
		if (levelPublicUuid != null) {
			courses = courseRepository.findAllByLevelPublicUuid(levelPublicUuid);
			if (isActive != null) {
				boolean wantActive = isActive;
				courses = courses.stream()
						.filter(c -> Boolean.valueOf(wantActive).equals(c.getIsActive()))
						.toList();
			}
		}
		else if (isActive != null) {
			courses = courseRepository.findAllByIsActiveSorted(isActive);
		}
		else {
			courses = courseRepository.findAllSorted();
		}

		if (courses.isEmpty()) return List.of();

		// Batch-fetch the pivots for all courses to avoid N+1.
		Map<UUID, List<CourseLevel>> linksByCourseId = batchFetchLevels(courses);

		return courses.stream()
				.map(c -> mapper.toListItem(c,
						linksByCourseId.getOrDefault(c.getId(), List.of())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CourseResponse getCourse(UUID publicUuid) {
		Course course = loadCourse(publicUuid);
		List<CourseLevel> links = courseLevelRepository.findAllByCourse(course);
		return mapper.toResponse(course, links);
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public CourseResponse createCourse(CreateCourseRequest request) {
		ensureCodeAvailable(request.code(), null);

		List<AcademicLevel> levels = resolveLevels(request.levelPublicUuids());

		Course course = mapper.fromCreate(request);
		Course persistedCourse;
		try {
			persistedCourse = courseRepository.saveAndFlush(course);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COURSE_CODE_TAKEN",
					"Another course in this tenant already uses the code '"
							+ request.code() + "'", ex);
		}

		List<CourseLevel> persistedLinks = persistLinks(persistedCourse, levels);

		log.info("[academic.course] created -- publicUuid={} code={} levels={}",
				persistedCourse.getPublicUuid(), persistedCourse.getCode(),
				levels.size());
		return mapper.toResponse(persistedCourse, persistedLinks);
	}

	@Override
	@Transactional
	public CourseResponse updateCourse(UUID publicUuid, UpdateCourseRequest request) {
		Course course = loadCourse(publicUuid);

		if (request == null || request.isEmpty()) {
			List<CourseLevel> links = courseLevelRepository.findAllByCourse(course);
			return mapper.toResponse(course, links);
		}

		if (request.code() != null
				&& !request.code().trim().equalsIgnoreCase(course.getCode())) {
			ensureCodeAvailable(request.code(), course.getId());
		}

		mapper.applyUpdate(request, course);

		try {
			Course saved = courseRepository.saveAndFlush(course);
			List<CourseLevel> links = courseLevelRepository.findAllByCourse(saved);
			log.info("[academic.course] updated -- publicUuid={} code={}",
					saved.getPublicUuid(), saved.getCode());
			return mapper.toResponse(saved, links);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COURSE_CODE_TAKEN",
					"Another course in this tenant already uses the code '"
							+ request.code() + "'", ex);
		}
	}

	@Override
	@Transactional
	public CourseResponse replaceLevels(UUID publicUuid, UpdateCourseLevelsRequest request) {
		Course course = loadCourse(publicUuid);
		List<AcademicLevel> targetLevels = resolveLevels(request.levelPublicUuids());

		// Diff the current set against the target set; only mutate what changed.
		List<CourseLevel> currentLinks = courseLevelRepository.findAllByCourse(course);
		Set<UUID> currentLevelIds = currentLinks.stream()
				.map(cl -> cl.getLevel().getId())
				.collect(java.util.stream.Collectors.toSet());
		Set<UUID> targetLevelIds = targetLevels.stream()
				.map(AcademicLevel::getId)
				.collect(java.util.stream.Collectors.toSet());

		// Removals: rows in current but not in target.
		List<CourseLevel> toRemove = currentLinks.stream()
				.filter(cl -> !targetLevelIds.contains(cl.getLevel().getId()))
				.toList();
		// Additions: levels in target but not yet linked.
		List<AcademicLevel> toAdd = targetLevels.stream()
				.filter(l -> !currentLevelIds.contains(l.getId()))
				.toList();

		if (!toRemove.isEmpty()) {
			courseLevelRepository.deleteAll(toRemove);
			courseLevelRepository.flush();
		}
		if (!toAdd.isEmpty()) {
			persistLinks(course, toAdd);
		}

		List<CourseLevel> finalLinks = courseLevelRepository.findAllByCourse(course);
		log.info("[academic.course] replaceLevels -- publicUuid={} added={} removed={} total={}",
				course.getPublicUuid(), toAdd.size(), toRemove.size(), finalLinks.size());
		return mapper.toResponse(course, finalLinks);
	}

	@Override
	@Transactional
	public void deleteCourse(UUID publicUuid) {
		Course course = loadCourse(publicUuid);

		// BE-4.7: reject delete when there are active assignments. The
		// FK on teacher_assignments(course_id) is RESTRICT — surface a
		// clean 409 instead of a generic integrity violation.
		if (teacherAssignmentRepository.existsActiveByCourse(course)) {
			throw new ConflictException("COURSE_IN_USE_BY_ASSIGNMENTS",
					"Course '" + course.getCode() + "' is used by active "
							+ "teacher assignments. Soft-end them first.");
		}

		// Soft-delete the pivot rows first so the FKs (RESTRICT) don't trip.
		List<CourseLevel> links = courseLevelRepository.findAllByCourse(course);
		if (!links.isEmpty()) {
			courseLevelRepository.deleteAll(links);
			courseLevelRepository.flush();
		}

		courseRepository.delete(course);
		log.info("[academic.course] deleted -- publicUuid={} code={}",
				course.getPublicUuid(), course.getCode());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Course loadCourse(UUID publicUuid) {
		return courseRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", publicUuid));
	}

	private void ensureCodeAvailable(String code, UUID excludeInternalId) {
		if (code == null) return;
		String normalised = code.trim();
		courseRepository.findByCodeIgnoreCase(normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("COURSE_CODE_TAKEN",
							"Another course in this tenant already uses the code '"
									+ normalised + "'");
				});
	}

	/**
	 * Resolves a list of level public UUIDs to entities, deduplicating
	 * silently and rejecting empty / unknown entries. The "at least one"
	 * invariant is enforced here, so callers don't need to repeat it.
	 */
	private List<AcademicLevel> resolveLevels(List<UUID> publicUuids) {
		if (publicUuids == null || publicUuids.isEmpty()) {
			throw new BusinessException("COURSE_NEEDS_AT_LEAST_ONE_LEVEL",
					"A course must be linked to at least one academic level.");
		}

		// De-duplicate while preserving order so error messages mention the
		// first offending UUID consistently.
		Set<UUID> seen = new HashSet<>();
		List<UUID> uniq = publicUuids.stream()
				.filter(java.util.Objects::nonNull)
				.filter(seen::add)
				.toList();

		if (uniq.isEmpty()) {
			throw new BusinessException("COURSE_NEEDS_AT_LEAST_ONE_LEVEL",
					"A course must be linked to at least one academic level.");
		}

		List<AcademicLevel> resolved = uniq.stream()
				.map(uuid -> levelRepository.findByPublicUuid(uuid)
						.orElseThrow(() -> new ResourceNotFoundException("AcademicLevel", uuid)))
				.toList();
		return resolved;
	}

	private List<CourseLevel> persistLinks(Course course, List<AcademicLevel> levels) {
		try {
			List<CourseLevel> persisted = new java.util.ArrayList<>(levels.size());
			for (AcademicLevel level : levels) {
				CourseLevel link = new CourseLevel();
				link.setCourse(course);
				link.setLevel(level);
				persisted.add(courseLevelRepository.save(link));
			}
			courseLevelRepository.flush();
			return persisted;
		}
		catch (DataIntegrityViolationException ex) {
			// uk_course_levels_course_level_active fired — concurrent
			// duplicate insert. The user view of "course is linked to N
			// levels" is unchanged so we report a generic conflict.
			throw new ConflictException("COURSE_LEVEL_DUPLICATE",
					"This course is already linked to one of the requested levels.", ex);
		}
	}

	/**
	 * Batched fetch of pivot rows for many courses at once. Keeps the
	 * insertion order of the input list so callers can iterate without
	 * re-sorting.
	 */
	private Map<UUID, List<CourseLevel>> batchFetchLevels(List<Course> courses) {
		List<CourseLevel> allLinks = courseLevelRepository.findAllByCourses(courses);
		Map<UUID, List<CourseLevel>> grouped = new LinkedHashMap<>();
		for (CourseLevel link : allLinks) {
			grouped.computeIfAbsent(link.getCourse().getId(),
					k -> new java.util.ArrayList<>()).add(link);
		}
		return grouped;
	}
}
