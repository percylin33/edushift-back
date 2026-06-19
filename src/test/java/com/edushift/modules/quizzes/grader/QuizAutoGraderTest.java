package com.edushift.modules.quizzes.grader;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuizAutoGrader} (Sprint 7b / BE-7b.1
 * + BE-7b.2). Pure JUnit + reflection. No Spring context, no
 * DB. The grader is a deterministic function of the answer
 * &amp; question state, so this is the only kind of test it
 * needs.
 *
 * <p>Since BE-7b.2, SHORT_ANSWER is also auto-graded (keyword
 * substring match) — the final verdict still comes from the
 * teacher via the manual grading queue, but the grader seeds
 * {@code points_awarded} on submit.
 */
class QuizAutoGraderTest {

	// ------------------------------------------------------------------
	// MC
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Multiple Choice (MC)")
	class McTests {

		@Test
		@DisplayName("selects the correct option → isCorrect=true, full points")
		void correctSelectionAwardsFullPoints() {
			McFixture fix = mcQuestion((short) 5);
			QuizOption correct = fix.correctOption();
			QuizAnswer a = answerWithSelectedOption(correct.getPublicUuid());

			QuizAutoGrader.grade(a, fix.q, fix.options);

			assertThat(a.getCorrect()).isTrue();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 5);
		}

		@Test
		@DisplayName("selects a wrong option → isCorrect=false, zero points")
		void wrongSelectionAwardsZero() {
			McFixture fix = mcQuestion((short) 5);
			QuizOption wrong = fix.options.stream()
					.filter(o -> !o.isCorrect())
					.findFirst().orElseThrow();
			QuizAnswer a = answerWithSelectedOption(wrong.getPublicUuid());

			QuizAutoGrader.grade(a, fix.q, fix.options);

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}

		@Test
		@DisplayName("no selection → isCorrect=false, zero points")
		void nullSelectionAwardsZero() {
			McFixture fix = mcQuestion((short) 5);
			QuizAnswer a = answerWithSelectedOption(null);

			QuizAutoGrader.grade(a, fix.q, fix.options);

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}

		@Test
		@DisplayName("selects a UUID that does not exist in the option set → incorrect")
		void orphanSelection() {
			McFixture fix = mcQuestion((short) 5);
			QuizAnswer a = answerWithSelectedOption(UUID.randomUUID());

			QuizAutoGrader.grade(a, fix.q, fix.options);

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}
	}

	// ------------------------------------------------------------------
	// TF
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("True / False (TF)")
	class TfTests {

		@Test
		@DisplayName("true vs true → correct")
		void tfBothTrue() {
			QuizQuestion q = tfQuestion(true, (short) 3);
			QuizAnswer a = answerWithSelectedBoolean(true);

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isTrue();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 3);
		}

		@Test
		@DisplayName("false vs true → incorrect")
		void tfWrongSide() {
			QuizQuestion q = tfQuestion(true, (short) 3);
			QuizAnswer a = answerWithSelectedBoolean(false);

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}

		@Test
		@DisplayName("null selectedBoolean → incorrect, zero points")
		void tfNullSelection() {
			QuizQuestion q = tfQuestion(false, (short) 2);
			QuizAnswer a = answerWithSelectedBoolean(null);

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}
	}

	// ------------------------------------------------------------------
	// SHORT_ANSWER
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Short Answer (SHORT_ANSWER) — BE-7b.2 keyword match")
	class ShortAnswerTests {

		@Test
		@DisplayName("all keywords present (case-insensitive) → correct, full points")
		void allKeywordsPresent() {
			QuizQuestion q = shortAnswerQuestion((short) 5,
					new String[]{"mitochondria", "ATP"});
			QuizAnswer a = answerWithTextAnswer(
					"The MITOCHONDRIA produces ATP for the cell.");

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isTrue();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 5);
		}

		@Test
		@DisplayName("one keyword missing → incorrect, zero points")
		void oneKeywordMissing() {
			QuizQuestion q = shortAnswerQuestion((short) 5,
					new String[]{"mitochondria", "ATP"});
			QuizAnswer a = answerWithTextAnswer("The nucleus stores DNA.");

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}

		@Test
		@DisplayName("empty text answer → incorrect, zero points")
		void emptyText() {
			QuizQuestion q = shortAnswerQuestion((short) 5,
					new String[]{"anything"});
			QuizAnswer a = answerWithTextAnswer("");

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isFalse();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 0);
		}

		@Test
		@DisplayName("no expected_keywords + non-blank text → any text is correct")
		void noKeywordsShortcutsToCorrect() {
			QuizQuestion q = shortAnswerQuestion((short) 5, null);
			QuizAnswer a = answerWithTextAnswer("anything goes");

			QuizAutoGrader.grade(a, q, List.of());

			assertThat(a.getCorrect()).isTrue();
			assertThat(a.getPointsAwarded()).isEqualTo((short) 5);
		}

		@Test
		@DisplayName("pure matcher: isShortAnswerMatch surfaces the same verdicts")
		void pureMatcher() {
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"The MITOCHONDRIA", new String[]{"mitochondria"})).isTrue();
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"nucleus", new String[]{"mitochondria"})).isFalse();
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"", new String[]{"x"})).isFalse();
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"hello", null)).isTrue();
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"hello", new String[]{})).isTrue();
			assertThat(QuizAutoGrader.isShortAnswerMatch(
					"hello", new String[]{"x", "  y  "})).isFalse();
		}
	}

	// ------------------------------------------------------------------
	// isAutoGradable
	// ------------------------------------------------------------------

	@Test
	@DisplayName("isAutoGradable covers MC/TF and (since BE-7b.2) SHORT_ANSWER")
	void autoGradablePredicate() {
		assertThat(QuizAutoGrader.isAutoGradable(QuestionType.MC)).isTrue();
		assertThat(QuizAutoGrader.isAutoGradable(QuestionType.TF)).isTrue();
		assertThat(QuizAutoGrader.isAutoGradable(QuestionType.SHORT_ANSWER)).isTrue();
	}

	// ------------------------------------------------------------------
	// builders
	// ------------------------------------------------------------------

	private static McFixture mcQuestion(short points) {
		List<QuizOption> opts = new ArrayList<>();
		opts.add(option("A", false, 1));
		opts.add(option("B", false, 2));
		opts.add(option("C", true, 3));  // correct
		opts.add(option("D", false, 4));
		QuizQuestion q = new QuizQuestion();
		setField(q, "questionType", QuestionType.MC);
		setField(q, "points", points);
		return new McFixture(q, opts);
	}

	private static QuizQuestion tfQuestion(boolean correctAnswer, short points) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "questionType", QuestionType.TF);
		setField(q, "points", points);
		setField(q, "correctBoolean", correctAnswer);
		return q;
	}

	private static QuizQuestion shortAnswerQuestion(short points,
			String[] keywords) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "questionType", QuestionType.SHORT_ANSWER);
		setField(q, "points", points);
		setField(q, "expectedKeywords", keywords);
		return q;
	}

	private static QuizAnswer answerWithTextAnswer(String text) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "textAnswer", text);
		return a;
	}

	private static QuizOption option(String label, boolean correct, int pos) {
		QuizOption o = new QuizOption();
		setField(o, "label", label);
		setField(o, "correct", correct);
		setField(o, "position", (short) pos);
		setField(o, "publicUuid", UUID.randomUUID());
		return o;
	}

	private static QuizAnswer answerWithSelectedOption(UUID optionId) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "selectedOptionId", optionId);
		return a;
	}

	private static QuizAnswer answerWithSelectedBoolean(Boolean value) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "selectedBoolean", value);
		return a;
	}

	private record McFixture(QuizQuestion q, List<QuizOption> options) {
		QuizOption correctOption() {
			return options.stream()
					.filter(QuizOption::isCorrect)
					.findFirst()
					.orElseThrow();
		}
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
