package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.AiPromptResponse;
import com.edushift.modules.ai.dto.SaveAiPromptRequest;
import com.edushift.modules.ai.entity.AiPrompt;
import com.edushift.modules.ai.mapper.AiPromptMapper;
import com.edushift.modules.ai.repository.AiPromptRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 18 / BE-18.5 — admin CRUD for {@code AiPrompt}.
 *
 * <h3>Why a service layer (vs straight controller → repo)</h3>
 * <ul>
 *   <li>The "activate" flag flip has a subtle invariant: only one
 *       row per {@code template_key} may be active at a time. The
 *       service does the flip atomically (within the same
 *       transaction) so the partial unique index never fires.</li>
 *   <li>The hot-path {@link #findActiveByKey(String)} call is cached
 *       with Spring's {@code @Cacheable} — a generation round-trip
 *       should never re-read the prompt from the DB. The cache is
 *       evicted on every write so a prompt edit takes effect on the
 *       next request after the admin saves.</li>
 *   <li>Version validation: the {@code (template_key, version)}
 *       unique constraint surfaces a 409 (Conflict) to the admin UI
 *       so they can bump the version label.</li>
 * </ul>
 */
@Service
public class PromptManagementService {

	/**
	 * Caffeine cache name (see {@code CacheConfiguration}). One region
	 * per template key so the admin's edits to one prompt don't
	 * invalidate the others.
	 */
	public static final String CACHE_ACTIVE = "ai-prompt-active";

	private final AiPromptRepository repository;
	private final AiPromptMapper mapper;

	public PromptManagementService(AiPromptRepository repository,
								  AiPromptMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	// --------------------------------------------------------------------
	// Read side
	// --------------------------------------------------------------------

	/**
	 * Hot path: the LLM builder calls this on every generation. Cached
	 * so we never hit the DB twice for the same template_key inside the
	 * TTL window.
	 */
	@Cacheable(value = CACHE_ACTIVE, key = "#templateKey")
	public AiPrompt findActiveByKey(String templateKey) {
		return repository.findActiveByTemplateKey(templateKey)
				.orElseThrow(() -> new ResourceNotFoundException(
						"AiPrompt(active)", templateKey));
	}

	/** All non-deleted versions of a template key, newest first. */
	public List<AiPromptResponse> listVersions(String templateKey) {
		return repository.findByTemplateKeyOrderByVersionDesc(templateKey)
				.stream()
				.map(mapper::toResponse)
				.toList();
	}

	/** Every template key currently in the system (admin index). */
	public List<String> listTemplateKeys() {
		return repository.findAllTemplateKeys();
	}

	// --------------------------------------------------------------------
	// Write side
	// --------------------------------------------------------------------

	/**
	 * Create or update a prompt.
	 *
	 * <p>On insert, validates that the {@code (template_key, version)}
	 * pair is unique. On update (the pair already exists), we just
	 * update the description / prompts and (optionally) flip the
	 * active flag. Both paths evict the active-prompt cache for
	 * the affected key.</p>
	 */
	@Transactional
	@CacheEvict(value = CACHE_ACTIVE, key = "#request.templateKey()")
	public AiPromptResponse save(SaveAiPromptRequest request) {
		var existing = repository.findByTemplateKeyAndVersion(
				request.templateKey(), request.version());

		AiPrompt saved;
		if (existing.isPresent()) {
			saved = existing.get();
			saved.setDescription(request.description());
			saved.setSystemPrompt(request.systemPrompt());
			saved.setUserPromptTemplate(request.userPromptTemplate());
			if (Boolean.TRUE.equals(request.activate())) {
				flipActive(saved);
			}
			// else: leave the active flag alone. The admin might
			// be editing a draft version that is not yet ready to
			// ship. Activation is explicit.
		}
		else {
			AiPrompt fresh = new AiPrompt();
			fresh.setTemplateKey(request.templateKey());
			fresh.setVersion(request.version());
			fresh.setDescription(request.description());
			fresh.setSystemPrompt(request.systemPrompt());
			fresh.setUserPromptTemplate(request.userPromptTemplate());
			fresh.setActive(false); // default off; activation is explicit
			if (Boolean.TRUE.equals(request.activate())) {
				// First persist the row (with active=false) so the
				// partial unique index doesn't fire, then flip.
				fresh = repository.saveAndFlush(fresh);
				flipActive(fresh);
			}
			saved = repository.saveAndFlush(fresh);
		}
		return mapper.toResponse(saved);
	}

	/**
	 * Explicit "make this the active version" operation, kept
	 * separate from {@link #save} so the admin can promote an
	 * existing version without re-uploading the text.
	 */
	@Transactional
	@CacheEvict(value = CACHE_ACTIVE, key = "#templateKey")
	public AiPromptResponse activate(String templateKey, String version) {
		AiPrompt target = repository.findByTemplateKeyAndVersion(templateKey, version)
				.orElseThrow(() -> new ResourceNotFoundException(
						"AiPrompt", templateKey + "/" + version));
		flipActive(target);
		return mapper.toResponse(repository.saveAndFlush(target));
	}

	/**
	 * Soft-delete. We keep the row around for audit (the AI generations
	 * table references it by id) but exclude it from all active
	 * queries via the {@code @SQLRestriction("deleted = false")} on
	 * {@link com.edushift.shared.domain.BaseEntity}.
	 */
	@Transactional
	@CacheEvict(value = CACHE_ACTIVE, key = "#templateKey")
	public void softDelete(String templateKey, String version) {
		AiPrompt target = repository.findByTemplateKeyAndVersion(templateKey, version)
				.orElseThrow(() -> new ResourceNotFoundException(
						"AiPrompt", templateKey + "/" + version));
		if (target.isActive()) {
			throw new BusinessException(
					"PROMPT_ACTIVE_CANNOT_DELETE",
					"Deactivate the prompt before deleting it (currently active).");
		}
		target.markDeleted();
		repository.saveAndFlush(target);
	}

	// --------------------------------------------------------------------
	// Internals
	// --------------------------------------------------------------------

	/**
	 * Single transaction: flip the current active row to inactive,
	 * then activate the target. The partial unique index
	 * {@code uk_ai_prompts_active_key} guarantees we can never end up
	 * with two active rows.
	 */
	private void flipActive(AiPrompt target) {
		repository.findActiveByTemplateKey(target.getTemplateKey())
				.ifPresent(current -> {
					if (!current.getId().equals(target.getId())) {
						current.setActive(false);
						repository.saveAndFlush(current);
					}
				});
		target.setActive(true);
	}

}
