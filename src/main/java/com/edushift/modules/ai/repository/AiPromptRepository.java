package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.AiPrompt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AiPrompt} (Sprint 18 / BE-18.5).
 *
 * <p>Prompts are <b>system-wide</b> — there is no {@code @TenantId}
 * discriminator on {@link AiPrompt}, so the JPA queries below return
 * rows regardless of the current tenant. That's intentional: the
 * platform ships one canonical prompt per use case, and per-tenant
 * overrides are a future feature.</p>
 *
 * <h3>Soft-delete semantics</h3>
 * <p>Extends {@code BaseEntity} which has a {@code deleted} flag +
 * global {@code @SQLRestriction("deleted = false")} filter. Every
 * query below is therefore implicitly "not soft-deleted" unless we
 * explicitly look at history (which the version-listing endpoint
 * does need to do — see {@link findByTemplateKeyOrderByVersionDesc}).</p>
 */
@Repository
public interface AiPromptRepository extends JpaRepository<AiPrompt, UUID> {

	/** Lookup by REST URL identifier (the same UUID as the PK). */
	Optional<AiPrompt> findById(UUID publicUuid);

	/**
	 * The ACTIVE row for a given template key. This is the LLM hot
	 * path — the prompt builder calls it on every generation. The
	 * partial unique index {@code uk_ai_prompts_active_key} ensures
	 * at most one row matches.
	 */
	@Query("""
			select p from AiPrompt p
			where p.templateKey = :key
			  and p.active = true
			""")
	Optional<AiPrompt> findActiveByTemplateKey(@Param("key") String templateKey);

	/**
	 * Lookup by natural key (template_key + version). Used by the
	 * service when the admin references a specific version.
	 */
	@Query("""
			select p from AiPrompt p
			where p.templateKey = :templateKey
			  and p.version = :version
			""")
	Optional<AiPrompt> findByTemplateKeyAndVersion(@Param("templateKey") String templateKey,
												   @Param("version") String version);

	/**
	 * All non-deleted versions of a template key, newest first. The
	 * admin UI shows this as a "history" tab so the operator can
	 * roll back to a previous version if a new one underperforms.
	 */
	@Query("""
			select p from AiPrompt p
			where p.templateKey = :key
			order by p.version desc
			""")
	List<AiPrompt> findByTemplateKeyOrderByVersionDesc(@Param("key") String templateKey);

	/**
	 * List all template keys currently in the system (one row per key,
	 * for the admin's index page).
	 */
	@Query("""
			select distinct p.templateKey from AiPrompt p
			order by p.templateKey
			""")
	List<String> findAllTemplateKeys();
}
