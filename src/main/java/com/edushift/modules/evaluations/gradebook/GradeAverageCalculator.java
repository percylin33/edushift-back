package com.edushift.modules.evaluations.gradebook;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-student grade average used by the teacher grade book and by
 * {@code GET /api/v1/me/grades/summary}. Both surfaces must show the same
 * number for the same assignment.
 */
public final class GradeAverageCalculator {

	private static final int AVERAGE_SCALE = 2;

	private GradeAverageCalculator() {
	}

	public record GradeKey(UUID evaluationId, UUID studentId) {
	}

	/**
	 * @param weightedAverage numeric course average, null on a CNEB sheet or when nothing counts
	 * @param literalAverage  AD/A/B/C when the sheet is {@code LITERAL_CNEB}, otherwise null
	 */
	public record Row(
			BigDecimal weightedAverage,
			String literalAverage,
			Map<UUID, BigDecimal> categoryAverages,
			Map<UUID, String> categoryLiterals
	) {
	}

	public static Row forStudent(
			UUID studentId,
			List<Evaluation> evaluations,
			Map<GradeKey, GradeRecord> gradeByKey,
			List<GradeBookCategoryEntry> categories,
			boolean literalSheet
	) {
		if (literalSheet) {
			Map<UUID, String> categoryLiterals = new LinkedHashMap<>();
			for (GradeBookCategoryEntry cat : categories) {
				if (!cat.contributesToAverage()) {
					continue;
				}
				BigDecimal points = computeLiteralPointsAverage(
						studentId, evaluations, gradeByKey, cat.publicUuid());
				if (points != null) {
					categoryLiterals.put(cat.publicUuid(), CnebLiteralScale.toLiteral(points));
				}
			}
			String literalAverage = categories.isEmpty()
					? CnebLiteralScale.toLiteral(computeLiteralPointsAverage(
							studentId, evaluations, gradeByKey, null))
					: computeLiteralCategoryAverage(categories, categoryLiterals);
			return new Row(null, literalAverage, Map.of(), categoryLiterals);
		}

		Map<UUID, BigDecimal> categoryAverages = new LinkedHashMap<>();
		for (GradeBookCategoryEntry cat : categories) {
			if (!cat.contributesToAverage()) {
				continue;
			}
			BigDecimal avg = computeWeightedAverage(
					studentId, evaluations, gradeByKey, cat.publicUuid());
			if (avg != null) {
				categoryAverages.put(cat.publicUuid(), avg);
			}
		}
		BigDecimal weightedAverage = categories.isEmpty()
				? computeWeightedAverage(studentId, evaluations, gradeByKey, null)
				: computeCategoryWeightedAverage(categories, categoryAverages);
		return new Row(weightedAverage, null, categoryAverages, Map.of());
	}

	public static BigDecimal computeWeightedAverage(
			UUID studentId,
			List<Evaluation> evaluations,
			Map<GradeKey, GradeRecord> gradeByKey,
			UUID categoryFilter
	) {
		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (Evaluation eval : evaluations) {
			if (!isContributingStatus(eval.getStatus())) continue;
			if (eval.getScale() != EvaluationScale.SCORE_0_20) continue;
			if (categoryFilter != null) {
				if (eval.getCategory() == null
						|| !categoryFilter.equals(eval.getCategory().getPublicUuid())) {
					continue;
				}
			}
			GradeRecord cell = gradeByKey.get(new GradeKey(eval.getId(), studentId));
			if (cell == null || cell.getScore() == null) continue;
			BigDecimal weight = Objects.requireNonNullElse(eval.getWeight(), BigDecimal.ZERO);
			numerator = numerator.add(cell.getScore().multiply(weight));
			denominator = denominator.add(weight);
		}
		if (denominator.signum() == 0) {
			return null;
		}
		return numerator.divide(denominator, AVERAGE_SCALE, RoundingMode.HALF_UP);
	}

	public static BigDecimal computeCategoryWeightedAverage(
			List<GradeBookCategoryEntry> categories,
			Map<UUID, BigDecimal> categoryAverages
	) {
		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (GradeBookCategoryEntry cat : categories) {
			if (!cat.contributesToAverage()) {
				continue;
			}
			BigDecimal avg = categoryAverages.get(cat.publicUuid());
			if (avg == null) {
				continue;
			}
			BigDecimal weight = Objects.requireNonNullElse(cat.weight(), BigDecimal.ZERO);
			numerator = numerator.add(avg.multiply(weight));
			denominator = denominator.add(weight);
		}
		if (denominator.signum() == 0) {
			return null;
		}
		return numerator.divide(denominator, AVERAGE_SCALE, RoundingMode.HALF_UP);
	}

	public static BigDecimal computeLiteralPointsAverage(
			UUID studentId,
			List<Evaluation> evaluations,
			Map<GradeKey, GradeRecord> gradeByKey,
			UUID categoryFilter
	) {
		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (Evaluation eval : evaluations) {
			if (!isContributingStatus(eval.getStatus())) continue;
			if (eval.getScale() != EvaluationScale.LITERAL_CNEB) continue;
			if (categoryFilter != null) {
				if (eval.getCategory() == null
						|| !categoryFilter.equals(eval.getCategory().getPublicUuid())) {
					continue;
				}
			}
			GradeRecord cell = gradeByKey.get(new GradeKey(eval.getId(), studentId));
			if (cell == null) continue;
			BigDecimal points = CnebLiteralScale.points(cell.getLiteral());
			if (points == null) continue;
			BigDecimal weight = Objects.requireNonNullElse(eval.getWeight(), BigDecimal.ZERO);
			numerator = numerator.add(points.multiply(weight));
			denominator = denominator.add(weight);
		}
		if (denominator.signum() == 0) {
			return null;
		}
		return numerator.divide(denominator, AVERAGE_SCALE, RoundingMode.HALF_UP);
	}

	public static String computeLiteralCategoryAverage(
			List<GradeBookCategoryEntry> categories,
			Map<UUID, String> categoryLiterals
	) {
		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (GradeBookCategoryEntry cat : categories) {
			if (!cat.contributesToAverage()) {
				continue;
			}
			BigDecimal points = CnebLiteralScale.points(categoryLiterals.get(cat.publicUuid()));
			if (points == null) {
				continue;
			}
			BigDecimal weight = Objects.requireNonNullElse(cat.weight(), BigDecimal.ZERO);
			numerator = numerator.add(points.multiply(weight));
			denominator = denominator.add(weight);
		}
		if (denominator.signum() == 0) {
			return null;
		}
		return CnebLiteralScale.toLiteral(
				numerator.divide(denominator, AVERAGE_SCALE, RoundingMode.HALF_UP));
	}

	public static boolean isContributingStatus(EvaluationStatus status) {
		return status == EvaluationStatus.PUBLISHED
				|| status == EvaluationStatus.CLOSED;
	}
}
