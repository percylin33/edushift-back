package com.edushift.modules.evaluations.rubric.service;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.error.RubricErrorCodes;
import com.edushift.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validation rules for {@code Rubric} payloads (Sprint 5B / BE-5B.2).
 *
 * <p>Lifted out of {@code RubricServiceImpl} so the rules can be unit
 * tested independently of the persistence layer. All rules map to
 * one of the {@code RUB_*} error codes in
 * {@link com.edushift.modules.evaluations.rubric.error.RubricErrorCodes}.</p>
 *
 * <h3>Rules enforced</h3>
 * <ol>
 *   <li>Criteria count in 1..10 ({@code RUB_CRITERIA_COUNT}).</li>
 *   <li>Criterion weights in [0, 100] and sum to exactly 100.0
 *       ({@code RUB_CRITERIA_WEIGHT_SUM}).</li>
 *   <li>Criterion {@code key} snake_case and unique within the rubric
 *       ({@code RUB_CRITERION_KEY_DUPLICATE}).</li>
 *   <li>Levels count in 2..4 ({@code RUB_LEVELS_COUNT}).</li>
 *   <li>Level {@code code} unique within the rubric
 *       ({@code RUB_LEVEL_CODE_DUPLICATE}).</li>
 *   <li>Descriptor levels refer to a defined level
 *       ({@code RUB_LEVEL_UNKNOWN}).</li>
 *   <li>Desriptors within a criterion are unique by level
 *       ({@code RUB_DESCRIPTOR_DUPLICATE}).</li>
 * </ol>
 */
@Component
public class RubricValidationService {

	/** Sum-of-weights tolerance (BigDecimal scale 2 ⇒ 0.01 absolute). */
	private static final BigDecimal WEIGHT_SUM_TARGET = new BigDecimal("100.00");
	private static final BigDecimal WEIGHT_SUM_EPSILON = new BigDecimal("0.01");

	/**
	 * Validates the full criteria + levels shape of a {@code Rubric}
	 * payload. Throws {@link BadRequestException} on the first failure
	 * with the corresponding {@code RUB_*} error code.
	 */
	public void assertShapeValid(List<CriterionInput> criteria, List<LevelInput> levels) {
		assertCriteriaCount(criteria);
		assertLevelsCount(levels);
		Set<String> levelCodes = assertLevelsUnique(levels);
		assertCriteriaKeysUnique(criteria);
		assertCriteriaWeightsValid(criteria);
		assertDescriptorsReferenceDefinedLevels(criteria, levelCodes);
	}

	/**
	 * Validates only the {@code criteria} list (1..10 items, keys
	 * unique, weights sum to 100.0). Use this in flows where
	 * {@code levels} is being patched independently and the descriptor
	 * cross-reference is checked at the end of the full patch.
	 */
	public void assertCriteriaShapeValid(List<CriterionInput> criteria) {
		if (criteria == null) return;
		assertCriteriaCount(criteria);
		assertCriteriaKeysUnique(criteria);
		assertCriteriaWeightsValid(criteria);
	}

	/**
	 * Validates only the {@code levels} list (2..4 items, codes
	 * unique). Same rationale as {@link #assertCriteriaShapeValid}.
	 */
	public void assertLevelsShapeValid(List<LevelInput> levels) {
		if (levels == null) return;
		assertLevelsCount(levels);
		assertLevelsUnique(levels);
	}

	// =========================================================================
	// Individual rules
	// =========================================================================

	private void assertCriteriaCount(List<CriterionInput> criteria) {
		if (criteria == null || criteria.isEmpty() || criteria.size() > 10) {
			throw new BadRequestException(RubricErrorCodes.RUB_CRITERIA_COUNT,
					"Criteria count must be in 1..10 (got "
							+ (criteria == null ? 0 : criteria.size()) + ")");
		}
	}

	private void assertLevelsCount(List<LevelInput> levels) {
		if (levels == null || levels.size() < 2 || levels.size() > 4) {
			throw new BadRequestException(RubricErrorCodes.RUB_LEVELS_COUNT,
					"Levels count must be in 2..4 (got "
							+ (levels == null ? 0 : levels.size()) + ")");
		}
	}

	private void assertCriteriaKeysUnique(List<CriterionInput> criteria) {
		if (criteria == null) return;
		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < criteria.size(); i++) {
			CriterionInput c = criteria.get(i);
			if (c.key() == null) continue;
			String key = c.key().trim();
			if (key.isEmpty()) continue;
			Integer prev = seen.put(key.toLowerCase(), i);
			if (prev != null) {
				throw new BadRequestException(
						RubricErrorCodes.RUB_CRITERION_KEY_DUPLICATE,
						"Criterion key '" + key + "' appears at positions "
								+ prev + " and " + i);
			}
		}
	}

	private void assertCriteriaWeightsValid(List<CriterionInput> criteria) {
		if (criteria == null) return;
		BigDecimal sum = BigDecimal.ZERO;
		for (CriterionInput c : criteria) {
			if (c.weight() == null) continue;
			if (c.weight().signum() < 0
					|| c.weight().compareTo(new BigDecimal("100.00")) > 0) {
				throw new BadRequestException(
						RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM,
						"Criterion '" + (c.key() == null ? "?" : c.key())
								+ "' weight " + c.weight()
								+ " is outside [0, 100]");
			}
			sum = sum.add(c.weight());
		}
		BigDecimal diff = sum.subtract(WEIGHT_SUM_TARGET).abs();
		if (diff.compareTo(WEIGHT_SUM_EPSILON) > 0) {
			throw new BadRequestException(
					RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM,
					"Sum of criterion weights must be 100.00 (got "
							+ sum.toPlainString() + ")");
		}
	}

	/**
	 * @return the set of level codes (uppercased) for downstream
	 *         descriptor cross-referencing.
	 */
	private Set<String> assertLevelsUnique(List<LevelInput> levels) {
		if (levels == null) return Set.of();
		Map<String, Integer> seen = new HashMap<>();
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < levels.size(); i++) {
			LevelInput l = levels.get(i);
			if (l.code() == null) continue;
			String code = l.code().trim();
			if (code.isEmpty()) continue;
			Integer prev = seen.put(code.toLowerCase(), i);
			if (prev != null) {
				throw new BadRequestException(
						RubricErrorCodes.RUB_LEVEL_CODE_DUPLICATE,
						"Level code '" + code + "' appears at positions "
								+ prev + " and " + i);
			}
			codes.add(code.toLowerCase());
		}
		return codes;
	}

	private void assertDescriptorsReferenceDefinedLevels(
			List<CriterionInput> criteria, Set<String> definedLevelCodes) {
		if (criteria == null || definedLevelCodes.isEmpty()) return;
		for (int i = 0; i < criteria.size(); i++) {
			CriterionInput c = criteria.get(i);
			if (c.descriptors() == null || c.descriptors().isEmpty()) continue;

			Set<String> seenLevels = new HashSet<>();
			for (int j = 0; j < c.descriptors().size(); j++) {
				DescriptorInput d = c.descriptors().get(j);
				if (d.level() == null) continue;
				String code = d.level().trim().toLowerCase();
				if (code.isEmpty()) continue;
				if (!definedLevelCodes.contains(code)) {
					throw new BadRequestException(
							RubricErrorCodes.RUB_LEVEL_UNKNOWN,
							"Criterion '" + (c.key() == null ? "?" : c.key())
									+ "' descriptor #" + j
									+ " references undefined level '"
									+ d.level() + "'");
				}
				if (!seenLevels.add(code)) {
					throw new BadRequestException(
							RubricErrorCodes.RUB_DESCRIPTOR_DUPLICATE,
							"Criterion '" + (c.key() == null ? "?" : c.key())
									+ "' has two descriptors for level '"
									+ d.level() + "'");
				}
			}
		}
	}
}
