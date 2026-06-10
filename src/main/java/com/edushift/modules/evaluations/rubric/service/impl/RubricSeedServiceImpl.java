package com.edushift.modules.evaluations.rubric.service.impl;

import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults;
import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults.DefaultCriterion;
import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults.DefaultDescriptor;
import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults.DefaultLevel;
import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults.DefaultRubric;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.evaluations.rubric.service.RubricSeedService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link RubricSeedService}. Materialises
 * the static {@link RubricSeedDefaults} catalog into the current
 * tenant on the first {@code GET /rubrics/system} of that tenant.
 *
 * <h3>Idempotency</h3>
 * The service checks the existing system-rubric names before
 * inserting. If a seed name is already present, that entry is skipped.
 * This makes the seed safe to re-run when the catalog grows
 * (Sprint N+1) and safe to call from multiple concurrent requests
 * (the unique index on {@code (tenant, lower(name))} catches races).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubricSeedServiceImpl implements RubricSeedService {

	private final RubricRepository rubricRepository;
	private final RubricMapper rubricMapper;

	@Override
	@Transactional
	public List<RubricListItem> materializeSystemRubrics() {
		// Collect names already present in this tenant (system + own).
		// We use the public API to leverage the @TenantId filter.
		List<Rubric> existing = rubricRepository.findFiltered(true, true, null);
		Set<String> present = new HashSet<>();
		for (Rubric r : existing) {
			present.add(r.getName().toLowerCase());
		}

		List<DefaultRubric> catalog = RubricSeedDefaults.all();
		int inserted = 0;
		int skipped = 0;
		for (DefaultRubric def : catalog) {
			if (present.contains(def.name().toLowerCase())) {
				skipped++;
				continue;
			}
			Rubric rubric = new Rubric();
			rubric.setName(def.name());
			rubric.setDescription(def.description());
			rubric.setCriteria(toCriteriaMaps(def.criteria()));
			rubric.setLevels(toLevelMaps(def.levels()));
			rubric.setIsSystem(Boolean.TRUE);
			rubric.setIsActive(Boolean.TRUE);
			rubric.setPublicUuid(UUID.randomUUID());
			rubricRepository.save(rubric);
			inserted++;
		}

		if (inserted > 0) {
			rubricRepository.flush();
			log.info("[evaluations.rubric.seed] materialised -- tenant={} "
							+ "inserted={} skipped={} catalogSize={}",
					"current", inserted, skipped, catalog.size());
		} else {
			log.debug("[evaluations.rubric.seed] no-op -- tenant={} "
							+ "skipped={} catalogSize={}",
					"current", skipped, catalog.size());
		}

		List<Rubric> refreshed = rubricRepository.findFiltered(true, true, null);
		List<RubricListItem> result = new ArrayList<>(refreshed.size());
		for (Rubric r : refreshed) {
			result.add(rubricMapper.toListItem(r));
		}
		return result;
	}

	// =========================================================================
	// DefaultX -> JSONB-map helpers (mirror the mapper semantics; the
	// validation below uses the typed views to surface drift early).
	// =========================================================================

	private static List<java.util.Map<String, Object>> toCriteriaMaps(List<DefaultCriterion> criteria) {
		List<java.util.Map<String, Object>> out = new ArrayList<>(criteria.size());
		for (DefaultCriterion c : criteria) {
			java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("key", c.key());
			m.put("name", c.name());
			if (c.description() != null) m.put("description", c.description());
			m.put("weight", c.weight());
			List<java.util.Map<String, Object>> descriptors = new ArrayList<>();
			if (c.descriptors() != null) {
				for (DefaultDescriptor d : c.descriptors()) {
					java.util.Map<String, Object> dm = new java.util.LinkedHashMap<>();
					dm.put("level", d.level());
					dm.put("text", d.text());
					descriptors.add(dm);
				}
			}
			m.put("descriptors", descriptors);
			out.add(m);
		}
		return out;
	}

	private static List<java.util.Map<String, Object>> toLevelMaps(List<DefaultLevel> levels) {
		List<java.util.Map<String, Object>> out = new ArrayList<>(levels.size());
		for (DefaultLevel l : levels) {
			java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("code", l.code());
			m.put("name", l.name());
			m.put("order", l.order());
			out.add(m);
		}
		return out;
	}
}
