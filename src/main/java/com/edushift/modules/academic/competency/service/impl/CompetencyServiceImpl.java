package com.edushift.modules.academic.competency.service.impl;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CompetencyMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.competency.service.CompetencyService;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Default implementation of {@link CompetencyService}.
 *
 * <p>Mirrors {@code UnitServiceImpl} (BE-5A.1): two-pass reorder, anti-
 * enumeration on cross-course UUIDs, placeholder for sessions count
 * until BE-5A.4 wires up.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencyServiceImpl implements CompetencyService {

	private final CompetencyRepository competencyRepository;
	private final CapacityRepository capacityRepository;
	private final CourseRepository courseRepository;
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
		ensureCodeAvailable(course, request.code(), null);

		int displayOrder = resolveDisplayOrder(course, request.displayOrder());
		Competency competency = mapper.fromCreate(request, course, displayOrder);

		Competency saved;
		try {
			saved = competencyRepository.saveAndFlush(competency);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("COMPETENCY_ORDER_TAKEN",
					"Another competency in this course already uses ordinal "
							+ displayOrder, ex);
		}

		log.info("[academic.competency] created -- publicUuid={} courseCode={} code={} order={}",
				saved.getPublicUuid(), course.getCode(), saved.getCode(),
				saved.getDisplayOrder());
		return mapper.toResponse(saved, List.of());
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
	 * Placeholder until BE-5A.4 wires up. Same rationale as
	 * {@code UnitServiceImpl.countSessionsByUnit}.
	 */
	private long countSessionsByCompetency(Competency competency) {
		// TODO BE-5A.4: replace with LearningSessionRepository.countActiveByCompetency(competency)
		return 0L;
	}
}
