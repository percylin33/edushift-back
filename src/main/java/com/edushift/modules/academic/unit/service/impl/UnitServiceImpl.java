package com.edushift.modules.academic.unit.service.impl;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.dto.UnitReorderRequest;
import com.edushift.modules.academic.unit.dto.UnitResponse;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.mapper.UnitMapper;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.academic.unit.service.UnitService;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * Default implementation of {@link UnitService}.
 *
 * <h3>Reorder strategy</h3>
 * Mirrors {@code GradeServiceImpl} (BE-4.2): a two-pass write where the
 * first pass parks every affected unit at a high temporary ordinal
 * (above the current max + buffer), then the second pass assigns the
 * final ordinal. This avoids tripping
 * {@code uk_academic_units_course_order_active} mid-update without
 * needing DEFERRED constraints and keeps every value compatible with
 * the {@code chk_academic_units_display_order_positive (display_order >= 1)}
 * CHECK.
 *
 * <h3>{@code UNIT_HAS_SESSIONS} (live since BE-5A.4)</h3>
 * Wired to {@link LearningSessionRepository#countActiveByUnit(Unit)}.
 * A unit can be hard-deleted only when no non-cancelled
 * {@code LearningSession} references it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitServiceImpl implements UnitService {

	private final UnitRepository unitRepository;
	private final CourseRepository courseRepository;
	private final LearningSessionRepository sessionRepository;
	private final UnitMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<UnitListItem> listUnits(UUID courseUuid, Boolean isActive) {
		Course course = loadCourse(courseUuid);

		List<Unit> units = unitRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		if (isActive != null) {
			boolean wantActive = isActive;
			units = units.stream()
					.filter(u -> Boolean.valueOf(wantActive).equals(u.getIsActive()))
					.toList();
		}

		if (units.isEmpty()) return List.of();

		Map<UUID, Long> countsByUnitId = batchSessionCounts(units);

		return units.stream()
				.map(u -> mapper.toListItem(u,
						countsByUnitId.getOrDefault(u.getId(), 0L)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public UnitResponse getUnit(UUID publicUuid) {
		Unit unit = loadUnit(publicUuid);
		return mapper.toResponse(unit, countSessionsByUnit(unit));
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public UnitResponse createUnit(UUID courseUuid, CreateUnitRequest request) {
		Course course = loadCourse(courseUuid);
		validateDates(request.startDate(), request.endDate());
		ensureNameAvailable(course, request.name(), null);

		int displayOrder = resolveDisplayOrder(course, request.displayOrder());
		Unit unit = mapper.fromCreate(request, course, displayOrder);

		Unit saved;
		try {
			saved = unitRepository.saveAndFlush(unit);
		}
		catch (DataIntegrityViolationException ex) {
			// uk_academic_units_course_order_active fired — concurrent
			// insert grabbed the same ordinal first.
			throw new ConflictException("UNIT_ORDER_TAKEN",
					"Another unit in this course already uses ordinal "
							+ displayOrder, ex);
		}

		log.info("[academic.unit] created -- publicUuid={} courseCode={} name={} order={}",
				saved.getPublicUuid(), course.getCode(), saved.getName(),
				saved.getDisplayOrder());
		return mapper.toResponse(saved, 0L);
	}

	@Override
	@Transactional
	public UnitResponse updateUnit(UUID publicUuid, UpdateUnitRequest request) {
		Unit unit = loadUnit(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(unit, countSessionsByUnit(unit));
		}

		// Resolve the effective dates considering the partial-merge
		// semantics so we validate the post-merge state, not the request
		// payload alone.
		LocalDate effectiveStart = request.startDate() != null
				? request.startDate() : unit.getStartDate();
		LocalDate effectiveEnd = request.endDate() != null
				? request.endDate() : unit.getEndDate();
		validateDates(effectiveStart, effectiveEnd);

		if (request.name() != null
				&& !request.name().trim().equalsIgnoreCase(unit.getName())) {
			ensureNameAvailable(unit.getCourse(), request.name(), unit.getId());
		}

		mapper.applyUpdate(request, unit);

		try {
			Unit saved = unitRepository.saveAndFlush(unit);
			log.info("[academic.unit] updated -- publicUuid={} name={}",
					saved.getPublicUuid(), saved.getName());
			return mapper.toResponse(saved, countSessionsByUnit(saved));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("UNIT_NAME_EXISTS",
					"Another unit in this course already uses the name '"
							+ request.name() + "'", ex);
		}
	}

	@Override
	@Transactional
	public List<UnitResponse> reorderUnits(UUID courseUuid, UnitReorderRequest request) {
		Course course = loadCourse(courseUuid);

		validateReorderPayload(request);

		List<Unit> courseUnits = unitRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		Map<UUID, Unit> byPublic = courseUnits.stream()
				.collect(Collectors.toMap(Unit::getPublicUuid, u -> u));

		// Bind each request item to a unit we own; reject cross-course
		// or unknown UUIDs with 409 UNIT_OUT_OF_COURSE (anti-enumeration:
		// a unit that exists but belongs elsewhere is rejected the same
		// way as one that does not exist).
		for (UnitReorderRequest.Item item : request.items()) {
			Unit target = byPublic.get(item.publicUuid());
			if (target == null) {
				throw new ConflictException("UNIT_OUT_OF_COURSE",
						"Unit " + item.publicUuid()
								+ " does not belong to course '"
								+ course.getCode() + "'");
			}
		}

		// Same two-pass strategy as GradeServiceImpl: park every affected
		// row at a temp ordinal above the current max + 1000 buffer.
		int existingMax = courseUnits.stream()
				.mapToInt(Unit::getDisplayOrder)
				.max().orElse(0);
		int parkBase = existingMax + 1000;
		int parkIdx = 0;
		for (UnitReorderRequest.Item item : request.items()) {
			Unit target = byPublic.get(item.publicUuid());
			target.setDisplayOrder(parkBase + parkIdx++);
			unitRepository.save(target);
		}
		unitRepository.flush();

		try {
			for (UnitReorderRequest.Item item : request.items()) {
				Unit target = byPublic.get(item.publicUuid());
				target.setDisplayOrder(item.displayOrder());
				unitRepository.save(target);
			}
			unitRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			// Final ordinal collided with a unit NOT included in the
			// reorder payload (the partial subset case).
			throw new ConflictException("UNIT_ORDER_TAKEN",
					"Reorder produced a duplicate ordinal in course '"
							+ course.getCode() + "'", ex);
		}

		log.info("[academic.unit] reordered -- courseCode={} count={}",
				course.getCode(), request.items().size());

		List<Unit> finalUnits = unitRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		Map<UUID, Long> countsByUnitId = batchSessionCounts(finalUnits);
		return finalUnits.stream()
				.map(u -> mapper.toResponse(u,
						countsByUnitId.getOrDefault(u.getId(), 0L)))
				.toList();
	}

	@Override
	@Transactional
	public void deleteUnit(UUID publicUuid) {
		Unit unit = loadUnit(publicUuid);

		long sessionCount = countSessionsByUnit(unit);
		if (sessionCount > 0) {
			throw new ConflictException("UNIT_HAS_SESSIONS",
					"Cannot delete unit '" + unit.getName()
							+ "': " + sessionCount + " learning session(s) "
							+ "still reference it. Soft-end them first.");
		}

		unitRepository.delete(unit);
		log.info("[academic.unit] deleted -- publicUuid={} name={}",
				unit.getPublicUuid(), unit.getName());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Course loadCourse(UUID publicUuid) {
		return courseRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", publicUuid));
	}

	private Unit loadUnit(UUID publicUuid) {
		return unitRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Unit", publicUuid));
	}

	private void ensureNameAvailable(Course course, String name, UUID excludeInternalId) {
		if (name == null) return;
		String normalised = name.trim();
		unitRepository.findByCourseAndNameIgnoreCase(course, normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("UNIT_NAME_EXISTS",
							"Another unit in course '" + course.getCode()
									+ "' already uses the name '" + normalised + "'");
				});
	}

	private static void validateDates(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new BadRequestException("UNIT_DATE_INVERTED",
					"Unit endDate (" + endDate + ") must be on or after startDate ("
							+ startDate + ")");
		}
	}

	private int resolveDisplayOrder(Course course, Integer requestedOrder) {
		if (requestedOrder == null) {
			Integer currentMax = unitRepository.findMaxDisplayOrderForCourse(course);
			return (currentMax == null ? 0 : currentMax) + 1;
		}
		return requestedOrder;
	}

	private static void validateReorderPayload(UnitReorderRequest request) {
		Set<Integer> seenOrders = new HashSet<>();
		Set<UUID> seenIds = new HashSet<>();
		for (UnitReorderRequest.Item item : request.items()) {
			if (!seenOrders.add(item.displayOrder())) {
				throw new ConflictException("UNIT_REORDER_INVALID",
						"Duplicate displayOrder " + item.displayOrder()
								+ " in reorder payload");
			}
			if (!seenIds.add(item.publicUuid())) {
				throw new ConflictException("UNIT_REORDER_INVALID",
						"Duplicate unit " + item.publicUuid()
								+ " in reorder payload");
			}
		}
	}

	/**
	 * Hot path: bulk-count active sessions per unit, used by the list
	 * endpoint to render badges without N+1 queries.
	 */
	private Map<UUID, Long> batchSessionCounts(List<Unit> units) {
		if (units == null || units.isEmpty()) return Map.of();
		Map<UUID, Long> result = new HashMap<>();
		for (Object[] row : sessionRepository.countActiveByUnitIn(units)) {
			result.put((UUID) row[0], ((Number) row[1]).longValue());
		}
		return result;
	}

	/**
	 * Per-unit active session count used by {@link #deleteUnit(UUID)}
	 * to surface {@code UNIT_HAS_SESSIONS} (409).
	 */
	private long countSessionsByUnit(Unit unit) {
		return sessionRepository.countActiveByUnit(unit);
	}
}
