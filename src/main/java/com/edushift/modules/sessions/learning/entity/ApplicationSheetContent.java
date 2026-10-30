package com.edushift.modules.sessions.learning.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pedagogical body of one {@link ApplicationSheet}, stored as JSONB.
 * The document is already written for a single grade: there are no
 * per-grade variants or columns.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationSheetContent implements Serializable {

	private static final long serialVersionUID = 1L;

	private Warmup warmup;
	private Purpose purpose;
	private List<Activity> activities = new ArrayList<>();
	private SelfAssessment selfAssessment;
	private Reflection reflection;
	private String closingMessage;

	private String subtitle;
	private String situation;
	private List<CompetencyRow> competencyMatrix = new ArrayList<>();
	private List<DiagnosticTask> tasks = new ArrayList<>();
	private List<AchievementLevel> achievementLevels = new ArrayList<>();
	private Boolean teacherComment;

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Warmup implements Serializable {
		private String prompt;
		private List<String> options = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Purpose implements Serializable {
		private String purpose;
		private String evidence;
		private String criterion;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Activity implements Serializable {
		private String title;
		private String moment;
		private String instruction;
		private List<Item> items = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Item implements Serializable {
		private String kind;
		private String prompt;
		private List<String> choices = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SelfAssessment implements Serializable {
		private String prompt;
		private List<String> columns = new ArrayList<>();
		private List<String> rows = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Reflection implements Serializable {
		private List<Item> questions = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CompetencyRow implements Serializable {
		private String competency;
		private String criterion;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DiagnosticTask implements Serializable {
		private String competency;
		private String title;
		private String stimulus;
		private List<Item> items = new ArrayList<>();
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AchievementLevel implements Serializable {
		private String competency;
		private String logro;
		private String proceso;
		private String inicio;
	}
}
