package com.edushift.modules.quizzes.mapper;

import com.edushift.modules.quizzes.dto.OptionResponse;
import com.edushift.modules.quizzes.dto.QuestionResponse;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.QuizSummary;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for the Quiz aggregate
 * (Sprint 7b / BE-7b.1).
 *
 * <h3>Type adaptation</h3>
 * The entity uses JPA-friendly types
 * ({@code Short}, {@code String[]}, primitive {@code boolean}).
 * The DTOs use JSON-friendly types ({@code int}, comma-separated
 * {@code String}, boxed {@code Boolean}). This mapper is the
 * single place that conversion happens so the FE never sees a
 * smallint wrapped in a Short.
 *
 * <h3>Why no {@code getQuestions()} / {@code getOptions()}?</h3>
 * The {@link Quiz} / {@link QuizQuestion} entities intentionally
 * do <em>not</em> model a JPA {@code @OneToMany} relationship
 * to their child rows (the relationship is expressed as a FK
 * column on the child side and resolved through the dedicated
 * repositories when needed). This keeps the entity graph
 * minimal and avoids cascade / orphanRemoval decisions we
 * don't currently need. The mapper therefore accepts the
 * question/option lists explicitly instead of traversing the
 * entity graph.
 *
 * <h3>Correctness flag</h3>
 * The {@code revealCorrectness} flag on {@link QuizResponse} is
 * not derived from the entity; it is supplied by the service
 * (which knows the caller's role). The mapper simply echoes it
 * through.
 */
@Component
public class QuizMapper {

	private final QuizQuestionRepository questionRepository;
	private final QuizOptionRepository optionRepository;

	public QuizMapper(QuizQuestionRepository questionRepository,
			QuizOptionRepository optionRepository) {
		this.questionRepository = questionRepository;
		this.optionRepository = optionRepository;
	}

	// ------------------------------------------------------------------
	// Quiz → QuizResponse / QuizSummary
	// ------------------------------------------------------------------

	public QuizResponse toResponse(Quiz entity, boolean revealCorrectness) {
		List<QuizQuestion> questions = questionRepository
				.findAllByQuizOrderByPositionAsc(entity);
		List<QuestionResponse> questionDtos = questions.stream()
				.map(this::toQuestionResponse)
				.toList();

		int totalPoints = questionDtos.stream()
				.mapToInt(q -> q.points())
				.sum();

		return new QuizResponse(
				entity.getPublicUuid(),
				entity.getSection() != null
						? entity.getSection().getPublicUuid() : null,
				entity.getTitle(),
				entity.getDescription(),
				entity.getStatus(),
				entity.getDueAt(),
				toInt(entity.getTimeLimitMinutes()),
				toInt(entity.getAttemptsAllowed()),
				toInt(entity.getMaxScore()),
				entity.getOwnerUserId(),
				entity.getPublishedAt(),
				entity.getClosedAt(),
				entity.getRubric() != null
						? entity.getRubric().getPublicUuid() : null,
				entity.getRubricEvaluation() != null
						? entity.getRubricEvaluation().getPublicUuid() : null,
				questionDtos.size(),
				totalPoints,
				revealCorrectness,
				questionDtos,
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	public QuizSummary toSummary(Quiz entity) {
		List<QuizQuestion> questions = questionRepository
				.findAllByQuizOrderByPositionAsc(entity);
		int totalPoints = questions.stream()
				.mapToInt(q -> q.getPoints() == null ? 0 : q.getPoints())
				.sum();
		return new QuizSummary(
				entity.getPublicUuid(),
				entity.getTitle(),
				entity.getStatus(),
				entity.getDueAt(),
				toInt(entity.getTimeLimitMinutes()),
				toInt(entity.getAttemptsAllowed()),
				toInt(entity.getMaxScore()),
				entity.getOwnerUserId(),
				questions.size(),
				totalPoints,
				entity.getCreatedAt());
	}

	// ------------------------------------------------------------------
	// QuizQuestion / QuizOption
	// ------------------------------------------------------------------

	public QuestionResponse toQuestionResponse(QuizQuestion entity) {
		List<QuizOption> options = optionRepository
				.findAllByQuestionOrderByPositionAsc(entity);
		List<OptionResponse> optionDtos = options.stream()
				.sorted(Comparator.comparingInt(
						o -> o.getPosition() == null ? 0 : o.getPosition()))
				.map(this::toOptionResponse)
				.toList();
		return new QuestionResponse(
				entity.getPublicUuid(),
				entity.getQuestionType(),
				entity.getPrompt(),
				entity.getPoints() == null ? 0 : entity.getPoints(),
				entity.getPosition() == null ? 0 : entity.getPosition(),
				null, // correctText — not modelled in entity yet (DEBT-BE-7B-4)
				joinKeywords(entity.getExpectedKeywords()),
				entity.getCorrectBoolean(),
				optionDtos);
	}

	public OptionResponse toOptionResponse(QuizOption entity) {
		return new OptionResponse(
				entity.getPublicUuid(),
				entity.getLabel(),
				entity.isCorrect(),
				null, // explanation — not modelled in entity yet (DEBT-BE-7B-4)
				entity.getPosition() == null ? 0 : entity.getPosition());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static Integer toInt(Short s) {
		return s == null ? null : s.intValue();
	}

	/** Join a {@code text[]} of keywords with commas. */
	private static String joinKeywords(String[] arr) {
		if (arr == null || arr.length == 0) {
			return null;
		}
		return String.join(",", arr);
	}
}
