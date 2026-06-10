package com.edushift.modules.evaluations.rubric.service.impl;

import com.edushift.modules.evaluations.rubric.dto.CreateRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.RubricFilters;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.dto.UpdateRubricRequest;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.error.RubricErrorCodes;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.evaluations.rubric.service.RubricSeedService;
import com.edushift.modules.evaluations.rubric.service.RubricService;
import com.edushift.modules.evaluations.rubric.service.RubricValidationService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link RubricService} (Sprint 5B / BE-5B.2).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>CRUD with tenant isolation enforced by Hibernate's
 *       {@code @TenantId} filter (see
 *       {@link com.edushift.shared.multitenancy}).</li>
 *   <li>Fork semantics: system rubrics are read-only; forking copies
 *       the JSONB payload and sets {@code parentRubric} +
 *       {@code isSystem = false}.</li>
 *   <li>Validation: the full shape (1..10 criteria, 2..4 levels,
 *       weight sum 100.0, level codes unique, descriptor references
 *       valid) is enforced via {@link RubricValidationService}.</li>
 *   <li>Seed materialisation: the first
 *       {@link #listSystemRubrics()} call per tenant triggers
 *       {@link RubricSeedService#materializeSystemRubrics()}.</li>
 * </ul>
 *
 * <h3>Soft-delete on system rubrics</h3>
 * The service rejects deletes on system rubrics with
 * {@code RUB_SYSTEM_READ_ONLY} (403). The DB's {@code ON DELETE RESTRICT}
 * on {@code parent_rubric_id} already blocks physical cascades; this
 * guard makes the error friendlier to FE callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubricServiceImpl implements RubricService {

	private final RubricRepository rubricRepository;
	private final RubricMapper rubricMapper;
	private final RubricValidationService validationService;
	private final RubricSeedService seedService;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<RubricListItem> listRubrics(RubricFilters filters) {
		RubricFilters effective = (filters == null)
				? new RubricFilters(null, null, null)
				: filters;
		String nameQuery = (effective.q() != null && !effective.q().isBlank())
				? effective.q().trim()
				: null;
		List<Rubric> rows = rubricRepository.findFiltered(
				effective.systemOnly(),
				effective.isActive(),
				nameQuery);
		if (rows.isEmpty()) return List.of();
		return rows.stream()
				.map(rubricMapper::toListItem)
				.toList();
	}

	@Override
	@Transactional
	public List<RubricListItem> listSystemRubrics() {
		// Idempotent on-demand seed.
		List<RubricListItem> systemList = seedService.materializeSystemRubrics();
		log.debug("[evaluations.rubric] system list -- size={}", systemList.size());
		return systemList;
	}

	@Override
	@Transactional(readOnly = true)
	public RubricResponse getRubric(UUID publicUuid) {
		Rubric rubric = loadRubric(publicUuid);
		return rubricMapper.toResponse(rubric);
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public RubricResponse createRubric(CreateRubricRequest request) {
		validationService.assertShapeValid(request.criteria(), request.levels());

		Rubric rubric = rubricMapper.fromCreate(
				request.name().trim(),
				request.description(),
				request.criteria(),
				request.levels());
		rubric.setPublicUuid(UUID.randomUUID());

		Rubric saved;
		try {
			saved = rubricRepository.saveAndFlush(rubric);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException(RubricErrorCodes.RUB_NAME_EXISTS,
					"Another rubric in this tenant already uses the name '"
							+ request.name() + "'", ex);
		}

		log.info("[evaluations.rubric] created -- publicUuid={} name={}",
				saved.getPublicUuid(), saved.getName());

		return rubricMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public RubricResponse forkRubric(UUID sourcePublicUuid, CreateRubricRequest request) {
		// The source is a system rubric. Use the cross-tenant lookup
		// because the system row's tenant_id is the same as the caller
		// (system rows are materialized per-tenant on first GET), but
		// we still need to ensure the source is_system=true.
		Rubric source = rubricRepository.findByPublicUuid(sourcePublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Rubric", sourcePublicUuid));
		if (!Boolean.TRUE.equals(source.getIsSystem())) {
			throw new BadRequestException(
					RubricErrorCodes.RUB_CANNOT_FORK_NON_SYSTEM,
					"Cannot fork a non-system rubric (" + sourcePublicUuid + ")");
		}

		// The forked rubric is always tenant-owned.
		String forkName = (request != null && request.name() != null
				&& !request.name().isBlank())
				? request.name().trim()
				: source.getName() + " (fork)";
		String description = (request != null && request.description() != null)
				? request.description()
				: source.getDescription();
		// For criteria and levels we always copy from the source unless
		// the caller explicitly provides new ones. The caller typically
		// wants to adjust; if they want an exact copy they can GET +
		// POST /create.
		var criteria = (request != null && request.criteria() != null
				&& !request.criteria().isEmpty())
				? request.criteria()
				: fromEntityCriteria(source);
		var levels = (request != null && request.levels() != null
				&& !request.levels().isEmpty())
				? request.levels()
				: fromEntityLevels(source);

		validationService.assertShapeValid(criteria, levels);

		Rubric fork = rubricMapper.fromCreate(forkName, description, criteria, levels);
		fork.setPublicUuid(UUID.randomUUID());
		fork.setParentRubric(source);
		fork.setIsSystem(Boolean.FALSE);

		Rubric saved;
		try {
			saved = rubricRepository.saveAndFlush(fork);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException(RubricErrorCodes.RUB_NAME_EXISTS,
					"Another rubric in this tenant already uses the name '"
							+ forkName + "'", ex);
		}

		log.info("[evaluations.rubric] forked -- source={} fork={} name='{}'",
				source.getPublicUuid(), saved.getPublicUuid(), saved.getName());

		return rubricMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public RubricResponse updateRubric(UUID publicUuid, UpdateRubricRequest request) {
		Rubric rubric = loadRubric(publicUuid);
		ensureMutable(rubric);

		if (request == null || request.isEmpty()) {
			return rubricMapper.toResponse(rubric);
		}

		// Resolve the effective shape against the post-merge state so
		// we can run the full validation as a single pass.
		// Names: if patch is null or blank, the existing name is kept.
		String effectiveName = (request.name() != null && !request.name().isBlank())
				? request.name().trim()
				: rubric.getName();
		var effectiveCriteria = (request.criteria() != null)
				? request.criteria()
				: fromEntityCriteria(rubric);
		var effectiveLevels = (request.levels() != null)
				? request.levels()
				: fromEntityLevels(rubric);

		validationService.assertShapeValid(effectiveCriteria, effectiveLevels);

		// Uniqueness: if the name is changing, the new name must be
		// available within the current tenant.
		if (!effectiveName.equalsIgnoreCase(rubric.getName())) {
			ensureNameAvailable(effectiveName, rubric.getId());
		}

		rubricMapper.applyUpdate(
				effectiveName,
				request.description(),
				effectiveCriteria,
				effectiveLevels,
				rubric);

		Rubric saved;
		try {
			saved = rubricRepository.saveAndFlush(rubric);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException(RubricErrorCodes.RUB_NAME_EXISTS,
					"Another rubric in this tenant already uses the name '"
							+ effectiveName + "'", ex);
		}

		log.info("[evaluations.rubric] updated -- publicUuid={} name='{}'",
				saved.getPublicUuid(), saved.getName());

		return rubricMapper.toResponse(saved);
	}

	// =========================================================================
	// Delete
	// =========================================================================

	@Override
	@Transactional
	public void deleteRubric(UUID publicUuid) {
		Rubric rubric = loadRubric(publicUuid);
		ensureMutable(rubric);

		rubricRepository.delete(rubric);
		log.info("[evaluations.rubric] deleted -- publicUuid={} name='{}'",
				publicUuid, rubric.getName());
	}

	// =========================================================================
	// Loaders
	// =========================================================================

	private Rubric loadRubric(UUID publicUuid) {
		return rubricRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Rubric", publicUuid));
	}

	// =========================================================================
	// Guards
	// =========================================================================

	/**
	 * Rejects mutations against system (MINEDU-seed) rubrics. The only
	 * valid operation on a system rubric is {@link #forkRubric}.
	 */
	private static void ensureMutable(Rubric rubric) {
		if (Boolean.TRUE.equals(rubric.getIsSystem())) {
			throw new ForbiddenException(
					RubricErrorCodes.RUB_SYSTEM_READ_ONLY,
					"Rubric " + rubric.getPublicUuid()
							+ " is a system (MINEDU-seed) rubric and is read-only; "
							+ "use POST /rubrics/" + rubric.getPublicUuid()
							+ "/fork to create a tenant-owned copy");
		}
	}

	private void ensureNameAvailable(String name, UUID excludeInternalId) {
		if (name == null) return;
		rubricRepository.findByNameIgnoreCase(name.trim())
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException(
							RubricErrorCodes.RUB_NAME_EXISTS,
							"Another rubric in this tenant already uses the name '"
									+ name + "'");
				});
	}

	// =========================================================================
	// Entity JSONB -> typed DTO conversions (for fork + update)
	// =========================================================================

	/**
	 * Reads the persisted {@code criteria} JSONB list and re-hydrates
	 * it as a {@code List<CriterionInput>} so the validation service
	 * and the mapper can run uniformly. The mapper's
	 * {@code toCriterionViews} is reused for the read step.
	 */
	private List<com.edushift.modules.evaluations.rubric.dto.CriterionInput>
			fromEntityCriteria(Rubric rubric) {
		var views = rubricMapper.toResponse(rubric).criteria();
		if (views == null) return List.of();
		List<com.edushift.modules.evaluations.rubric.dto.CriterionInput> out =
				new java.util.ArrayList<>(views.size());
		for (var v : views) {
			List<com.edushift.modules.evaluations.rubric.dto.DescriptorInput> desc =
					v.descriptors() == null
							? List.of()
							: v.descriptors().stream()
									.map(d -> new com.edushift.modules.evaluations.rubric.dto.DescriptorInput(
											d.level(), d.text()))
									.toList();
			out.add(new com.edushift.modules.evaluations.rubric.dto.CriterionInput(
					v.key(),
					v.name(),
					v.description(),
					v.weight(),
					desc));
		}
		return out;
	}

	private List<com.edushift.modules.evaluations.rubric.dto.LevelInput>
			fromEntityLevels(Rubric rubric) {
		var views = rubricMapper.toResponse(rubric).levels();
		if (views == null) return List.of();
		List<com.edushift.modules.evaluations.rubric.dto.LevelInput> out =
				new java.util.ArrayList<>(views.size());
		for (var v : views) {
			out.add(new com.edushift.modules.evaluations.rubric.dto.LevelInput(
					v.code(), v.name(), v.order()));
		}
		return out;
	}
}
