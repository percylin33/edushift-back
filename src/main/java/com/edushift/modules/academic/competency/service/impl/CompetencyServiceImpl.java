package com.edushift.modules.academic.competency.service.impl;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyCapacityRequest;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.entity.CompetencyOrigin;
import com.edushift.modules.academic.competency.mapper.CompetencyMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.competency.service.CompetencyService;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CompetencyService}.
 *
 * <p>Mirrors {@code UnitServiceImpl} (BE-5A.1): two-pass reorder, anti-
 * enumeration on cross-course UUIDs, and (since BE-5A.4) a real
 * sessions-count probe for {@code COMPETENCY_IN_USE_BY_SESSIONS}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencyServiceImpl implements CompetencyService {

	private static final int CODE_MAX = 40;

	private final CompetencyRepository competencyRepository;
	private final CapacityRepository capacityRepository;
	private final CourseRepository courseRepository;
	private final LearningSessionRepository sessionRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final CurrentUserProvider currentUser;
	private final CompetencyMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<CompetencyListItem> listCompetencies(UUID courseUuid, Boolean isActive) {
		Course course = loadCourse(courseUuid);

		List<Competency> competencies =
				competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		if (isActive != null) {
			boolean wantActive = isActive;
			competencies = competencies.stream()
					.filter(c -> Boolean.valueOf(wantActive).equals(c.getIsActive()))
					.toList();
		}

		if (competencies.isEmpty()) return List.of();

		Map<UUID, Long> countsByCompetencyId = batchCapacityCounts(competencies);

		return competencies.stream()
				.map(c -> mapper.toListItem(c,
						countsByCompetencyId.getOrDefault(c.getId(), 0L)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CompetencyResponse getCompetency(UUID publicUuid) {
		Competency competency = loadCompetency(publicUuid);
		List<Capacity> capacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(competency);
		return mapper.toResponse(competency, capacities);
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public CompetencyResponse createCompetency(UUID courseUuid,
			CreateCompetencyRequest request) {
		Course course = loadCourse(courseUuid);
		boolean teacher = isTeacher();
		if (teacher) {
			requireAssigned(course);
			if (request.capacities() == null || request.capacities().isEmpty()) {
				throw new BadRequestException("COMPETENCY_NEEDS_CAPACITY",
						"A teacher-created competency needs at least one capacity");
			}
		}

		String code = resolveCode(course, request.code(), request.name());
		ensureCodeAvailable(course, code, null);

		int displayOrder = resolveDisplayOrder(course, request.displayOrder());
		Competency competency = mapper.fromCreate(request, course, displayOrder);
		competency.setCode(code);
		competency.setOrigin(teacher
				? CompetencyOrigin.TEACHER
				: CompetencyOrigin.INSTITUTIONAL);

		Competency saved;
		try {
			saved = competencyRepository.saveAndFlush(competency);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COMPETENCY_ORDER_TAKEN",
					"Another competency in this course already uses ordinal "
							+ displayOrder, ex);
		}

		List<Capacity> capacities = persistCapacities(saved, request.capacities());

		log.info("[academic.competency] created -- publicUuid={} courseCode={} code={} origin={} order={}",
				saved.getPublicUuid(), course.getCode(), saved.getCode(),
				saved.getOrigin(), saved.getDisplayOrder());
		return mapper.toResponse(saved, capacities);
	}

	@Override
	@Transactional
	public CompetencyResponse updateCompetency(UUID publicUuid,
			UpdateCompetencyRequest request) {
		Competency competency = loadCompetency(publicUuid);

		if (request == null || request.isEmpty()) {
			List<Capacity> capacities = capacityRepository
					.findAllByCompetencyOrderByDisplayOrderAsc(competency);
			return mapper.toResponse(competency, capacities);
		}

		if (request.code() != null
				&& !request.code().trim().equalsIgnoreCase(competency.getCode())) {
			ensureCodeAvailable(competency.getCourse(), request.code(), competency.getId());
		}

		mapper.applyUpdate(request, competency);

		try {
			Competency saved = competencyRepository.saveAndFlush(competency);
			List<Capacity> capacities = capacityRepository
					.findAllByCompetencyOrderByDisplayOrderAsc(saved);
			log.info("[academic.competency] updated -- publicUuid={} code={}",
					saved.getPublicUuid(), saved.getCode());
			return mapper.toResponse(saved, capacities);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COMPETENCY_CODE_TAKEN",
					"Another competency in this course already uses the code '"
							+ request.code() + "'", ex);
		}
	}

	@Override
	@Transactional
	public List<CompetencyResponse> reorderCompetencies(UUID courseUuid,
			CompetencyReorderRequest request) {
		Course course = loadCourse(courseUuid);

		validateReorderPayload(request);

		List<Competency> courseCompetencies =
				competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		Map<UUID, Competency> byPublic = courseCompetencies.stream()
				.collect(Collectors.toMap(Competency::getPublicUuid, c -> c));

		for (CompetencyReorderRequest.Item item : request.items()) {
			Competency target = byPublic.get(item.publicUuid());
			if (target == null) {
				throw new ConflictException("COMPETENCY_OUT_OF_COURSE",
						"Competency " + item.publicUuid()
								+ " does not belong to course '"
								+ course.getCode() + "'");
			}
		}

		int existingMax = courseCompetencies.stream()
				.mapToInt(Competency::getDisplayOrder)
				.max().orElse(0);
		int parkBase = existingMax + 1000;
		int parkIdx = 0;
		for (CompetencyReorderRequest.Item item : request.items()) {
			Competency target = byPublic.get(item.publicUuid());
			target.setDisplayOrder(parkBase + parkIdx++);
			competencyRepository.save(target);
		}
		competencyRepository.flush();

		try {
			for (CompetencyReorderRequest.Item item : request.items()) {
				Competency target = byPublic.get(item.publicUuid());
				target.setDisplayOrder(item.displayOrder());
				competencyRepository.save(target);
			}
			competencyRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COMPETENCY_ORDER_TAKEN",
					"Reorder produced a duplicate ordinal in course '"
							+ course.getCode() + "'", ex);
		}

		log.info("[academic.competency] reordered -- courseCode={} count={}",
				course.getCode(), request.items().size());

		List<Competency> finalList =
				competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		List<Capacity> allCapacities = finalList.isEmpty()
				? List.of()
				: capacityRepository.findAllByCompetencyIn(finalList);
		Map<UUID, List<Capacity>> capacitiesByCompetencyId = groupByCompetencyId(allCapacities);

		return finalList.stream()
				.map(c -> mapper.toResponse(c,
						capacitiesByCompetencyId.getOrDefault(c.getId(), List.of())))
				.toList();
	}

	@Override
	@Transactional
	public void deleteCompetency(UUID publicUuid) {
		Competency competency = loadCompetency(publicUuid);

		long sessionCount = countSessionsByCompetency(competency);
		if (sessionCount > 0) {
			throw new ConflictException("COMPETENCY_IN_USE_BY_SESSIONS",
					"Cannot delete competency '" + competency.getCode()
							+ "': " + sessionCount + " learning session(s) "
							+ "still reference it. Soft-end them first.");
		}

		List<Capacity> capacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(competency);
		if (!capacities.isEmpty()) {
			capacityRepository.deleteAll(capacities);
			capacityRepository.flush();
		}

		competencyRepository.delete(competency);
		log.info("[academic.competency] deleted -- publicUuid={} code={} cascadedCapacities={}",
				competency.getPublicUuid(), competency.getCode(), capacities.size());
	}

	@Override
	@Transactional
	public CompetencyResponse deactivateCompetency(UUID publicUuid) {
		Competency competency = loadCompetency(publicUuid);
		if (isTeacher()) {
			requireAssigned(competency.getCourse());
			UUID userId = currentUserId();
			boolean owned = competency.getOrigin() == CompetencyOrigin.TEACHER
					&& userId.equals(competency.getCreatedBy());
			if (!owned) {
				throw new ForbiddenException("COMPETENCY_ORIGIN_LOCKED",
						"Only the teacher who created this competency can deactivate it");
			}
			if (sessionRepository.existsActiveByCompetencyForOtherTeacher(competency, userId)) {
				throw new ConflictException("COMPETENCY_USED_BY_OTHER_TEACHER",
						"Another teacher already uses competency '"
								+ competency.getCode() + "' in a learning session");
			}
		}

		competency.setIsActive(Boolean.FALSE);
		Competency saved = competencyRepository.saveAndFlush(competency);
		List<Capacity> capacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(saved);
		log.info("[academic.competency] deactivated -- publicUuid={} origin={}",
				saved.getPublicUuid(), saved.getOrigin());
		return mapper.toResponse(saved, capacities);
	}

	@Override
	@Transactional
	public CompetencyResponse promoteCompetency(UUID publicUuid) {
		if (isTeacher()) {
			throw new ForbiddenException("COMPETENCY_ORIGIN_LOCKED",
					"Only a school admin can promote a competency");
		}
		Competency competency = loadCompetency(publicUuid);
		if (competency.getOrigin() != CompetencyOrigin.TEACHER) {
			throw new ConflictException("COMPETENCY_ORIGIN_LOCKED",
					"Only a teacher-created competency can be promoted");
		}
		competency.setOrigin(CompetencyOrigin.INSTITUTIONAL);
		Competency saved = competencyRepository.saveAndFlush(competency);
		List<Capacity> capacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(saved);
		log.info("[academic.competency] promoted -- publicUuid={}", saved.getPublicUuid());
		return mapper.toResponse(saved, capacities);
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Course loadCourse(UUID publicUuid) {
		return courseRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", publicUuid));
	}

	private Competency loadCompetency(UUID publicUuid) {
		return competencyRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Competency", publicUuid));
	}

	private void ensureCodeAvailable(Course course, String code, UUID excludeInternalId) {
		if (code == null) return;
		String normalised = code.trim();
		competencyRepository.findByCourseAndCodeIgnoreCase(course, normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("COMPETENCY_CODE_TAKEN",
							"Another competency in course '" + course.getCode()
									+ "' already uses the code '" + normalised + "'");
				});
	}

	private int resolveDisplayOrder(Course course, Integer requestedOrder) {
		if (requestedOrder == null) {
			Integer currentMax = competencyRepository.findMaxDisplayOrderForCourse(course);
			return (currentMax == null ? 0 : currentMax) + 1;
		}
		return requestedOrder;
	}

	private static void validateReorderPayload(CompetencyReorderRequest request) {
		Set<Integer> seenOrders = new HashSet<>();
		Set<UUID> seenIds = new HashSet<>();
		for (CompetencyReorderRequest.Item item : request.items()) {
			if (!seenOrders.add(item.displayOrder())) {
				throw new ConflictException("COMPETENCY_REORDER_INVALID",
						"Duplicate displayOrder " + item.displayOrder()
								+ " in reorder payload");
			}
			if (!seenIds.add(item.publicUuid())) {
				throw new ConflictException("COMPETENCY_REORDER_INVALID",
						"Duplicate competency " + item.publicUuid()
								+ " in reorder payload");
			}
		}
	}

	/**
	 * Bulk capacity counts for a list of competencies (single grouped query
	 * to avoid N+1).
	 */
	private Map<UUID, Long> batchCapacityCounts(List<Competency> competencies) {
		List<Capacity> all = capacityRepository.findAllByCompetencyIn(competencies);
		Map<UUID, Long> counts = new LinkedHashMap<>();
		for (Capacity c : all) {
			counts.merge(c.getCompetency().getId(), 1L, Long::sum);
		}
		return counts;
	}

	private static Map<UUID, List<Capacity>> groupByCompetencyId(List<Capacity> capacities) {
		Map<UUID, List<Capacity>> grouped = new LinkedHashMap<>();
		for (Capacity c : capacities) {
			grouped.computeIfAbsent(c.getCompetency().getId(),
					k -> new java.util.ArrayList<>()).add(c);
		}
		return grouped;
	}

	/**
	 * Active session count used by {@link #deleteCompetency(UUID)}
	 * to surface {@code COMPETENCY_IN_USE_BY_SESSIONS} (409). Wired
	 * to {@link LearningSessionRepository} since BE-5A.4.
	 */
	private long countSessionsByCompetency(Competency competency) {
		return sessionRepository.countActiveByCompetency(competency);
	}

	private boolean isTeacher() {
		return currentUser.currentUserRole().orElse(UserRole.TENANT_ADMIN) == UserRole.TEACHER;
	}

	private UUID currentUserId() {
		return currentUser.currentUserId().orElseThrow(() ->
				new ForbiddenException("TEACHER_NOT_ASSIGNED_TO_COURSE",
						"No authenticated teacher for this competency write"));
	}

	private void requireAssigned(Course course) {
		UUID userId = currentUserId();
		if (!assignmentRepository.existsActiveByCourseAndTeacherUserId(course, userId)) {
			throw new ForbiddenException("TEACHER_NOT_ASSIGNED_TO_COURSE",
					"You can only author competencies for a course you teach");
		}
	}

	private String resolveCode(Course course, String requested, String name) {
		if (requested != null && !requested.isBlank()) {
			return requested.trim();
		}
		String base = codeFromName(name);
		String candidate = base;
		int suffix = 2;
		while (competencyRepository.findByCourseAndCodeIgnoreCase(course, candidate).isPresent()) {
			candidate = withSuffix(base, suffix++);
			if (suffix > 50) {
				throw new ConflictException("COMPETENCY_CODE_TAKEN",
						"Could not derive a free code from '" + name + "'");
			}
		}
		return candidate;
	}

	private List<Capacity> persistCapacities(Competency competency,
			List<CreateCompetencyCapacityRequest> requested) {
		if (requested == null || requested.isEmpty()) {
			return List.of();
		}
		Set<String> used = new HashSet<>();
		List<Capacity> saved = new ArrayList<>(requested.size());
		int order = 1;
		for (CreateCompetencyCapacityRequest item : requested) {
			String code = item.code() == null || item.code().isBlank()
					? codeFromName(item.name())
					: item.code().trim();
			String unique = code;
			int suffix = 2;
			while (!used.add(unique.toUpperCase(Locale.ROOT))) {
				unique = withSuffix(code, suffix++);
			}
			Capacity capacity = new Capacity();
			capacity.setCompetency(competency);
			capacity.setCode(unique);
			capacity.setName(item.name().trim());
			capacity.setDisplayOrder(order++);
			capacity.setIsActive(Boolean.TRUE);
			saved.add(capacityRepository.save(capacity));
		}
		capacityRepository.flush();
		return saved;
	}

	private static String codeFromName(String name) {
		String stripped = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		String normalized = stripped.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
			normalized = "C_" + normalized;
			normalized = normalized.replaceAll("_+$", "");
		}
		if (normalized.length() > CODE_MAX) {
			normalized = normalized.substring(0, CODE_MAX).replaceAll("_+$", "");
		}
		if (normalized.isEmpty()) {
			normalized = "C";
		}
		return normalized;
	}

	private static String withSuffix(String base, int suffix) {
		String tail = "_" + suffix;
		int room = CODE_MAX - tail.length();
		String stem = base.length() > room ? base.substring(0, Math.max(room, 1)) : base;
		stem = stem.replaceAll("_+$", "");
		if (stem.isEmpty()) {
			stem = "C";
		}
		String candidate = stem + tail;
		return candidate.length() > CODE_MAX
				? candidate.substring(0, CODE_MAX)
				: candidate;
	}
}
