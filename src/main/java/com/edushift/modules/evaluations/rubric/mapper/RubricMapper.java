package com.edushift.modules.evaluations.rubric.mapper;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.CriterionView;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.DescriptorView;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.dto.LevelView;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for
 * {@link com.edushift.modules.evaluations.rubric.entity.Rubric}
 * (Sprint 5B / BE-5B.2). Same convention as the rest of the codebase
 * (no MapStruct).
 *
 * <h3>JSONB ↔ typed records</h3>
 * The entity stores {@code criteria} and {@code levels} as
 * {@code List<Map<String, Object>>} (JSONB); the API exposes them as
 * typed records ({@link CriterionInput}, {@link LevelInput} and their
 * {@code View} counterparts). This mapper is the only place that
 * crosses the two shapes, so the rest of the codebase stays type-safe.
 *
 * <h3>Stable JSON shape</h3>
 * The DTO → map conversion is deterministic (insertion-ordered via
 * {@link LinkedHashMap}) so two equivalent payloads produce identical
 * JSONB on disk. The DB unique index on {@code lower(name)} is the
 * only uniqueness invariant — the JSON shape is opaque to the DB.
 */
@Component
public class RubricMapper {

	// =========================================================================
	// Entity -> response DTOs
	// =========================================================================

	public RubricResponse toResponse(Rubric rubric) {
		UUID parentUuid = rubric.getParentRubric() != null
				? rubric.getParentRubric().getPublicUuid()
				: null;
		return new RubricResponse(
				rubric.getPublicUuid(),
				rubric.getName(),
				rubric.getDescription(),
				toCriterionViews(rubric.getCriteria()),
				toLevelViews(rubric.getLevels()),
				rubric.getIsSystem(),
				parentUuid,
				rubric.getIsActive(),
				rubric.getCreatedAt(),
				rubric.getUpdatedAt()
		);
	}

	public RubricListItem toListItem(Rubric rubric) {
		List<Map<String, Object>> criteria = safeList(rubric.getCriteria());
		List<String> summary = new ArrayList<>(criteria.size());
		for (Map<String, Object> c : criteria) {
			String name = asString(c.get("name"));
			BigDecimal weight = asBigDecimal(c.get("weight"));
			if (name != null && weight != null) {
				summary.add("%s (%s%%)".formatted(name, weight.stripTrailingZeros().toPlainString()));
			} else if (name != null) {
				summary.add(name);
			}
		}
		UUID parentUuid = rubric.getParentRubric() != null
				? rubric.getParentRubric().getPublicUuid()
				: null;
		return new RubricListItem(
				rubric.getPublicUuid(),
				rubric.getName(),
				rubric.getDescription(),
				rubric.getIsSystem(),
				parentUuid,
				criteria.size(),
				summary,
				rubric.getIsActive(),
				rubric.getCreatedAt(),
				rubric.getUpdatedAt()
		);
	}

	// =========================================================================
	// Create request -> entity
	// =========================================================================

	public Rubric fromCreate(String name, String description,
			List<CriterionInput> criteria, List<LevelInput> levels) {
		Rubric rubric = new Rubric();
		rubric.setName(name);
		rubric.setDescription(blankToNull(description));
		rubric.setCriteria(criteriaToMaps(criteria));
		rubric.setLevels(levelsToMaps(levels));
		rubric.setIsSystem(Boolean.FALSE);
		rubric.setIsActive(Boolean.TRUE);
		return rubric;
	}

	// =========================================================================
	// Update patch -> entity (partial-merge)
	// =========================================================================

	/**
	 * Applies a partial-merge patch. The service is responsible for
	 * (a) rejecting patches on system rubrics and (b) running the
	 * full revalidation of the resulting shape (weight sum, level
	 * count, descriptor references). This method only copies fields.
	 */
	public void applyUpdate(String name, String description,
			List<CriterionInput> criteria, List<LevelInput> levels, Rubric rubric) {
		if (name != null) {
			rubric.setName(name);
		}
		if (description != null) {
			rubric.setDescription(blankToNull(description));
		}
		if (criteria != null) {
			rubric.setCriteria(criteriaToMaps(criteria));
		}
		if (levels != null) {
			rubric.setLevels(levelsToMaps(levels));
		}
	}

	// =========================================================================
	// Helpers: typed records <-> JSONB maps
	// =========================================================================

	private static List<CriterionView> toCriterionViews(List<Map<String, Object>> criteria) {
		if (criteria == null) return List.of();
		List<CriterionView> out = new ArrayList<>(criteria.size());
		for (Map<String, Object> c : criteria) {
			List<DescriptorInput> descIn = toDescriptorInputs(c.get("descriptors"));
			List<DescriptorView> descOut = new ArrayList<>(descIn.size());
			for (DescriptorInput d : descIn) {
				descOut.add(new DescriptorView(d.level(), d.text()));
			}
			out.add(new CriterionView(
					asString(c.get("key")),
					asString(c.get("name")),
					asString(c.get("description")),
					asBigDecimal(c.get("weight")),
					descOut));
		}
		return out;
	}

	private static List<LevelView> toLevelViews(List<Map<String, Object>> levels) {
		if (levels == null) return List.of();
		List<LevelView> out = new ArrayList<>(levels.size());
		for (Map<String, Object> l : levels) {
			out.add(new LevelView(
					asString(l.get("code")),
					asString(l.get("name")),
					asInteger(l.get("order"))));
		}
		return out;
	}

	private static List<DescriptorInput> toDescriptorInputs(Object raw) {
		if (!(raw instanceof List<?> list)) return List.of();
		List<DescriptorInput> out = new ArrayList<>(list.size());
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> m)) continue;
			String level = asString(m.get("level"));
			String text = asString(m.get("text"));
			if (level != null && text != null) {
				out.add(new DescriptorInput(level, text));
			}
		}
		return out;
	}

	/**
	 * Convert a list of typed criteria into the JSONB-friendly
	 * {@code List<Map<String, Object>>}. Maps are insertion-ordered
	 * ({@link LinkedHashMap}) so two equivalent inputs produce
	 * identical bytes on disk.
	 */
	private static List<Map<String, Object>> criteriaToMaps(List<CriterionInput> criteria) {
		List<Map<String, Object>> out = new ArrayList<>(criteria.size());
		for (CriterionInput c : criteria) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("key", c.key());
			m.put("name", c.name());
			if (c.description() != null) m.put("description", c.description());
			m.put("weight", c.weight());
			List<Map<String, Object>> descriptors = new ArrayList<>();
			if (c.descriptors() != null) {
				for (DescriptorInput d : c.descriptors()) {
					Map<String, Object> dm = new LinkedHashMap<>();
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

	private static List<Map<String, Object>> levelsToMaps(List<LevelInput> levels) {
		List<Map<String, Object>> out = new ArrayList<>(levels.size());
		int idx = 0;
		for (LevelInput l : levels) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", l.code());
			m.put("name", l.name());
			int order = l.order() != null ? l.order() : idx++;
			m.put("order", order);
			out.add(m);
		}
		return out;
	}

	private static <T> List<T> safeList(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static String asString(Object value) {
		if (value == null) return null;
		return value.toString();
	}

	private static BigDecimal asBigDecimal(Object value) {
		if (value == null) return null;
		if (value instanceof BigDecimal bd) return bd;
		if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
		try {
			return new BigDecimal(value.toString());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer asInteger(Object value) {
		if (value == null) return null;
		if (value instanceof Integer i) return i;
		if (value instanceof Number n) return n.intValue();
		try {
			return Integer.parseInt(value.toString());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
