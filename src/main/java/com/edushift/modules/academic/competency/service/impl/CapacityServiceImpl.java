package com.edushift.modules.academic.competency.service.impl;

import com.edushift.modules.academic.competency.dto.CapacityReorderRequest;
import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CapacityMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.competency.service.CapacityService;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
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
 * Default implementation of {@link CapacityService}.
 *
 * <p>Mirrors {@link CompetencyServiceImpl}, just one level deeper in the
 * tree. Same two-pass reorder + anti-enumeration on cross-competency UUIDs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapacityServiceImpl implements CapacityService {

	private final CapacityRepository capacityRepository;
	private final CompetencyRepository competencyRepository;
	private final LearningSessionRepository sessionRepository;
	private final CapacityMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<CapacityResponse> listCapacities(UUID competencyUuid, Boolean isActive) {
		Competency competency = loadCompetency(competencyUuid);

		List<Capacity> capacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(competency);
		if (isActive != null) {
			boolean wantActive = isActive;
			capacities = capacities.stream()
					.filter(c -> Boolean.valueOf(wantActive).equals(c.getIsActive()))
					.toList();
		}

		return capacities.stream().map(mapper::toResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CapacityResponse getCapacity(UUID publicUuid) {
		Capacity capacity = loadCapacity(publicUuid);
		return mapper.toResponse(capacity);
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public CapacityResponse createCapacity(UUID competencyUuid,
			CreateCapacityRequest request) {
		Competency competency = loadCompetency(competencyUuid);
		ensureCodeAvailable(competency, request.code(), null);

		int displayOrder = resolveDisplayOrder(competency, request.displayOrder());
		Capacity capacity = mapper.fromCreate(request, competency, displayOrder);

		Capacity saved;
		try {
			saved = capacityRepository.saveAndFlush(capacity);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("CAPACITY_ORDER_TAKEN",
					"Another capacity in this competency already uses ordinal "
							+ displayOrder, ex);
		}

		log.info("[academic.capacity] created -- publicUuid={} competencyCode={} code={} order={}",
				saved.getPublicUuid(), competency.getCode(), saved.getCode(),
				saved.getDisplayOrder());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public CapacityResponse updateCapacity(UUID publicUuid, UpdateCapacityRequest request) {
		Capacity capacity = loadCapacity(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(capacity);
		}

		if (request.code() != null
				&& !request.code().trim().equalsIgnoreCase(capacity.getCode())) {
			ensureCodeAvailable(capacity.getCompetency(), request.code(), capacity.getId());
		}

		mapper.applyUpdate(request, capacity);

		try {
			Capacity saved = capacityRepository.saveAndFlush(capacity);
			log.info("[academic.capacity] updated -- publicUuid={} code={}",
					saved.getPublicUuid(), saved.getCode());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("CAPACITY_CODE_TAKEN",
					"Another capacity in this competency already uses the code '"
							+ request.code() + "'", ex);
		}
	}

	@Override
	@Transactional
	public List<CapacityResponse> reorderCapacities(UUID competencyUuid,
			CapacityReorderRequest request) {
		Competency competency = loadCompetency(competencyUuid);

		validateReorderPayload(request);

		List<Capacity> ownedCapacities = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(competency);
		Map<UUID, Capacity> byPublic = ownedCapacities.stream()
				.collect(Collectors.toMap(Capacity::getPublicUuid, c -> c));

		for (CapacityReorderRequest.Item item : request.items()) {
			Capacity target = byPublic.get(item.publicUuid());
			if (target == null) {
				throw new ConflictException("CAPACITY_OUT_OF_COMPETENCY",
						"Capacity " + item.publicUuid()
								+ " does not belong to competency '"
								+ competency.getCode() + "'");
			}
		}

		int existingMax = ownedCapacities.stream()
				.mapToInt(Capacity::getDisplayOrder)
				.max().orElse(0);
		int parkBase = existingMax + 1000;
		int parkIdx = 0;
		for (CapacityReorderRequest.Item item : request.items()) {
			Capacity target = byPublic.get(item.publicUuid());
			target.setDisplayOrder(parkBase + parkIdx++);
			capacityRepository.save(target);
		}
		capacityRepository.flush();

		try {
			for (CapacityReorderRequest.Item item : request.items()) {
				Capacity target = byPublic.get(item.publicUuid());
				target.setDisplayOrder(item.displayOrder());
				capacityRepository.save(target);
			}
			capacityRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("CAPACITY_ORDER_TAKEN",
					"Reorder produced a duplicate ordinal in competency '"
							+ competency.getCode() + "'", ex);
		}

		log.info("[academic.capacity] reordered -- competencyCode={} count={}",
				competency.getCode(), request.items().size());

		List<Capacity> finalList = capacityRepository
				.findAllByCompetencyOrderByDisplayOrderAsc(competency);
		return finalList.stream().map(mapper::toResponse).toList();
	}

	@Override
	@Transactional
	public void deleteCapacity(UUID publicUuid) {
		Capacity capacity = loadCapacity(publicUuid);

		long sessionCount = countSessionsByCapacity(capacity);
		if (sessionCount > 0) {
			throw new ConflictException("CAPACITY_IN_USE_BY_SESSIONS",
					"Cannot delete capacity '" + capacity.getCode()
							+ "': " + sessionCount + " learning session(s) "
							+ "still reference it. Soft-end them first.");
		}

		capacityRepository.delete(capacity);
		log.info("[academic.capacity] deleted -- publicUuid={} code={}",
				capacity.getPublicUuid(), capacity.getCode());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Competency loadCompetency(UUID publicUuid) {
		return competencyRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Competency", publicUuid));
	}

	private Capacity loadCapacity(UUID publicUuid) {
		return capacityRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Capacity", publicUuid));
	}

	private void ensureCodeAvailable(Competency competency, String code,
			UUID excludeInternalId) {
		if (code == null) return;
		String normalised = code.trim();
		capacityRepository.findByCompetencyAndCodeIgnoreCase(competency, normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("CAPACITY_CODE_TAKEN",
							"Another capacity in competency '" + competency.getCode()
									+ "' already uses the code '" + normalised + "'");
				});
	}

	private int resolveDisplayOrder(Competency competency, Integer requestedOrder) {
		if (requestedOrder == null) {
			Integer currentMax = capacityRepository
					.findMaxDisplayOrderForCompetency(competency);
			return (currentMax == null ? 0 : currentMax) + 1;
		}
		return requestedOrder;
	}

	private static void validateReorderPayload(CapacityReorderRequest request) {
		Set<Integer> seenOrders = new HashSet<>();
		Set<UUID> seenIds = new HashSet<>();
		for (CapacityReorderRequest.Item item : request.items()) {
			if (!seenOrders.add(item.displayOrder())) {
				throw new ConflictException("CAPACITY_REORDER_INVALID",
						"Duplicate displayOrder " + item.displayOrder()
								+ " in reorder payload");
			}
			if (!seenIds.add(item.publicUuid())) {
				throw new ConflictException("CAPACITY_REORDER_INVALID",
						"Duplicate capacity " + item.publicUuid()
								+ " in reorder payload");
			}
		}
	}

	private long countSessionsByCapacity(Capacity capacity) {
		return sessionRepository.countActiveByCapacity(capacity);
	}
}
