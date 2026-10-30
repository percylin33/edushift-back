package com.edushift.modules.quizzes.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.quizzes.dto.OptionResponse;
import com.edushift.modules.quizzes.dto.QuestionResponse;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.QuizSummary;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuizMapper} (Sprint 7b / BE-7b.1).
 *
 * <p>Verifies the JSON-friendly type adaptation
 * ({@code Short → int}, {@code String[] → CSV}, sort order, total
 * points aggregation) and the {@code revealCorrectness} pass-through.
 *
 * <p>Repos are mocked to avoid pulling in Spring or a DB.
 */
class QuizMapperTest {

	private QuizQuestionRepository questionRepository;
	private QuizOptionRepository optionRepository;
	private QuizMapper mapper;

	@BeforeEach
	void setUp() {
		questionRepository = mock(QuizQuestionRepository.class);
		optionRepository = mock(QuizOptionRepository.class);
		mapper = new QuizMapper(questionRepository, optionRepository);
	}

	@Test
	@DisplayName("toResponse aggregates totalPoints from the question list")
	void responseAggregatesTotalPoints() {
		Quiz q = quiz();
		List<QuizQuestion> qs = List.of(
				question(QuestionType.TF, (short) 1, (short) 1),
				question(QuestionType.MC, (short) 3, (short) 2),
				question(QuestionType.SHORT_ANSWER, (short) 5, (short) 3));
		// The repository is responsible for ordering; the mock returns
		// already-sorted-by-position data.
		when(questionRepository.findAllByQuizOrderByPositionAsc(q))
				.thenReturn(qs);
		when(optionRepository.findAllByQuestionInOrderByPositionAsc(qs))
				.thenReturn(List.of());

		QuizResponse r = mapper.toResponse(q, /* revealCorrectness */ true);

		assertThat(r.questionCount()).isEqualTo(3);
		assertThat(r.totalPoints()).isEqualTo(1 + 3 + 5);
		assertThat(r.questions()).extracting(QuestionResponse::position)
				.containsExactly(1, 2, 3);
		assertThat(r.revealCorrectness()).isTrue();
		verify(optionRepository, times(1)).findAllByQuestionInOrderByPositionAsc(qs);
		verify(optionRepository, never()).findAllByQuestionOrderByPositionAsc(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("toResponse assigns each option to its question from a single load")
	void responseGroupsOptionsInOneQuery() {
		Quiz q = quiz();
		QuizQuestion q1 = question(QuestionType.MC, (short) 1, (short) 1);
		QuizQuestion q2 = question(QuestionType.MC, (short) 1, (short) 2);
		setField(q1, "id", UUID.randomUUID());
		setField(q2, "id", UUID.randomUUID());
		List<QuizQuestion> qs = List.of(q1, q2);
		QuizOption a = option("A", true, 1);
		setField(a, "question", q1);
		QuizOption b = option("B", false, 1);
		setField(b, "question", q2);
		when(questionRepository.findAllByQuizOrderByPositionAsc(q)).thenReturn(qs);
		when(optionRepository.findAllByQuestionInOrderByPositionAsc(qs))
				.thenReturn(List.of(a, b));

		QuizResponse r = mapper.toResponse(q, true);

		assertThat(r.questions().get(0).options()).extracting(OptionResponse::label)
				.containsExactly("A");
		assertThat(r.questions().get(1).options()).extracting(OptionResponse::label)
				.containsExactly("B");
	}

	@Test
	@DisplayName("toResponse propagates revealCorrectness=false for taker screens")
	void responseRevealCorrectnessFalse() {
		Quiz q = quiz();
		when(questionRepository.findAllByQuizOrderByPositionAsc(q))
				.thenReturn(List.of());

		QuizResponse r = mapper.toResponse(q, false);
		assertThat(r.revealCorrectness()).isFalse();
	}

	@Test
	@DisplayName("toSummary uses the pre-aggregated totals and does not load questions")
	void summaryHasNoQuestions() {
		Quiz q = quiz();

		QuizSummary s = mapper.toSummary(q, 1, 2);
		assertThat(s.questionCount()).isEqualTo(1);
		assertThat(s.totalPoints()).isEqualTo(2);
		assertThat(s.status()).isEqualTo(QuizStatus.DRAFT);
		verify(questionRepository, never()).findAllByQuizOrderByPositionAsc(q);
	}

	@Test
	@DisplayName("toQuestionResponse preserves option order from the repository")
	void optionsSortedByPosition() {
		QuizQuestion q = question(QuestionType.MC, (short) 1, (short) 1);
		// Repository returns already-ordered-by-position data.
		List<QuizOption> opts = List.of(
				option("A", true, 1),
				option("B", false, 2),
				option("C", false, 3));
		when(optionRepository.findAllByQuestionInOrderByPositionAsc(List.of(q)))
				.thenReturn(opts);

		QuestionResponse r = mapper.toQuestionResponse(q);
		assertThat(r.options()).extracting(OptionResponse::position)
				.containsExactly(1, 2, 3);
	}

	@Test
	@DisplayName("expectedKeywords array is joined with commas")
	void expectedKeywordsJoined() {
		QuizQuestion q = question(QuestionType.SHORT_ANSWER, (short) 1, (short) 1);
		setField(q, "expectedKeywords", new String[]{"k1", "k2", "k3"});
		when(optionRepository.findAllByQuestionInOrderByPositionAsc(List.of(q)))
				.thenReturn(List.of());

		QuestionResponse r = mapper.toQuestionResponse(q);
		assertThat(r.expectedKeywords()).isEqualTo("k1,k2,k3");
	}

	@Test
	@DisplayName("null expectedKeywords returns null (no NPE)")
	void nullKeywordsReturnsNull() {
		QuizQuestion q = question(QuestionType.MC, (short) 1, (short) 1);
		when(optionRepository.findAllByQuestionInOrderByPositionAsc(List.of(q)))
				.thenReturn(List.of());

		QuestionResponse r = mapper.toQuestionResponse(q);
		assertThat(r.expectedKeywords()).isNull();
	}

	// ------------------------------------------------------------------
	// builders
	// ------------------------------------------------------------------

	private static Quiz quiz() {
		Quiz q = new Quiz();
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "title", "test");
		setField(q, "status", QuizStatus.DRAFT);
		setField(q, "maxScore", (short) 100);
		setField(q, "attemptsAllowed", (short) 1);
		return q;
	}

	private static QuizQuestion question(QuestionType type, short points, short position) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "questionType", type);
		setField(q, "points", points);
		setField(q, "position", position);
		setField(q, "prompt", "prompt");
		return q;
	}

	private static QuizOption option(String label, boolean correct, int pos) {
		QuizOption o = new QuizOption();
		setField(o, "publicUuid", UUID.randomUUID());
		setField(o, "label", label);
		setField(o, "correct", correct);
		setField(o, "position", (short) pos);
		return o;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = findField(target.getClass(), name);
			f.setAccessible(true);
			f.set(target, value);
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to set " + name + " on "
					+ target.getClass().getSimpleName(), ex);
		}
	}

	private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
		while (c != null) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}
}
