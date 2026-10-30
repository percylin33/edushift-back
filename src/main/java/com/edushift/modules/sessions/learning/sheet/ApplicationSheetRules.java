package com.edushift.modules.sessions.learning.sheet;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.AchievementLevel;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.Activity;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.CompetencyRow;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.DiagnosticTask;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.Item;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Structural checks for a single-grade application sheet.
 * Returns the first problem, or empty when the document is usable.
 */
public final class ApplicationSheetRules {

	private static final Set<String> ITEM_KINDS = Set.of("LINE", "CHECKBOX", "CHOICE");

	private ApplicationSheetRules() {
	}

	public static Optional<String> validate(ApplicationSheetKind kind, String grade,
			String title, ApplicationSheetContent content) {
		if (grade == null || grade.isBlank() || grade.length() > 80) {
			return Optional.of("grade is required and must be 1..80 chars");
		}
		if (title == null || title.isBlank() || title.length() < 5 || title.length() > 200) {
			return Optional.of("title must be 5..200 chars");
		}
		if (content == null) {
			return Optional.of("content is required");
		}
		if (kind == ApplicationSheetKind.DIAGNOSTIC) {
			return validateDiagnostic(content);
		}
		return validateLearning(content);
	}

	private static Optional<String> validateLearning(ApplicationSheetContent content) {
		ApplicationSheetContent.Warmup warmup = content.getWarmup();
		if (warmup == null || blank(warmup.getPrompt())) {
			return Optional.of("warmup.prompt is required");
		}
		if (warmup.getOptions() == null || warmup.getOptions().size() < 2 || warmup.getOptions().size() > 6) {
			return Optional.of("warmup.options must have 2..6 items");
		}
		ApplicationSheetContent.Purpose purpose = content.getPurpose();
		if (purpose == null || blank(purpose.getPurpose()) || blank(purpose.getEvidence())
				|| blank(purpose.getCriterion())) {
			return Optional.of("purpose, evidence and criterion are required for this grade");
		}
		List<Activity> activities = content.getActivities();
		if (activities == null || activities.isEmpty() || activities.size() > 6) {
			return Optional.of("activities must have 1..6 items");
		}
		for (int i = 0; i < activities.size(); i++) {
			Activity activity = activities.get(i);
			if (activity == null || blank(activity.getTitle())) {
				return Optional.of("activities[" + i + "].title is required");
			}
			Optional<String> itemError = validateItems(activity.getItems(), "activities[" + i + "].items");
			if (itemError.isPresent()) {
				return itemError;
			}
		}
		ApplicationSheetContent.SelfAssessment assessment = content.getSelfAssessment();
		if (assessment == null || assessment.getColumns() == null || assessment.getColumns().size() != 2) {
			return Optional.of("selfAssessment.columns must have exactly 2 labels");
		}
		if (assessment.getRows() == null || assessment.getRows().isEmpty() || assessment.getRows().size() > 8) {
			return Optional.of("selfAssessment.rows must have 1..8 items");
		}
		ApplicationSheetContent.Reflection reflection = content.getReflection();
		if (reflection == null) {
			return Optional.of("reflection is required");
		}
		return validateItems(reflection.getQuestions(), "reflection.questions", 1, 4);
	}

	private static Optional<String> validateDiagnostic(ApplicationSheetContent content) {
		if (blank(content.getSituation())) {
			return Optional.of("situation is required");
		}
		List<CompetencyRow> matrix = content.getCompetencyMatrix();
		if (matrix == null || matrix.isEmpty() || matrix.size() > 6) {
			return Optional.of("competencyMatrix must have 1..6 rows");
		}
		for (int i = 0; i < matrix.size(); i++) {
			CompetencyRow row = matrix.get(i);
			if (row == null || blank(row.getCompetency()) || blank(row.getCriterion())) {
				return Optional.of("competencyMatrix[" + i + "] needs competency and criterion");
			}
		}
		List<DiagnosticTask> tasks = content.getTasks();
		if (tasks == null || tasks.isEmpty() || tasks.size() > 4) {
			return Optional.of("tasks must have 1..4 items");
		}
		for (int i = 0; i < tasks.size(); i++) {
			DiagnosticTask task = tasks.get(i);
			if (task == null || blank(task.getTitle())) {
				return Optional.of("tasks[" + i + "].title is required");
			}
			Optional<String> itemError = validateItems(task.getItems(), "tasks[" + i + "].items");
			if (itemError.isPresent()) {
				return itemError;
			}
		}
		List<AchievementLevel> levels = content.getAchievementLevels();
		if (levels == null || levels.isEmpty() || levels.size() > 6) {
			return Optional.of("achievementLevels must have 1..6 rows");
		}
		for (int i = 0; i < levels.size(); i++) {
			AchievementLevel level = levels.get(i);
			if (level == null || blank(level.getCompetency()) || blank(level.getLogro())
					|| blank(level.getProceso()) || blank(level.getInicio())) {
				return Optional.of("achievementLevels[" + i + "] needs competency, logro, proceso and inicio");
			}
		}
		return Optional.empty();
	}

	private static Optional<String> validateItems(List<Item> items, String path) {
		return validateItems(items, path, 1, 8);
	}

	private static Optional<String> validateItems(List<Item> items, String path, int min, int max) {
		if (items == null || items.size() < min || items.size() > max) {
			return Optional.of(path + " must have " + min + ".." + max + " items");
		}
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			if (item == null || item.getKind() == null || item.getKind().isBlank()) {
				return Optional.of(path + "[" + i + "].kind is required");
			}
			String kind = item.getKind().trim().toUpperCase(Locale.ROOT);
			if (!ITEM_KINDS.contains(kind)) {
				return Optional.of(path + "[" + i + "].kind must be LINE, CHECKBOX or CHOICE");
			}
			item.setKind(kind);
			if (blank(item.getPrompt())) {
				return Optional.of(path + "[" + i + "].prompt is required");
			}
			if ("CHOICE".equals(kind)) {
				List<String> choices = item.getChoices();
				if (choices == null || choices.size() < 2 || choices.size() > 6) {
					return Optional.of(path + "[" + i + "].choices must have 2..6 items");
				}
			}
		}
		return Optional.empty();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
