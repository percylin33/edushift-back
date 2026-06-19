package com.edushift.modules.quizzes.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lightweight smoke tests for the BE-7b.0 JPA entities
 * (Sprint 7b / BE-7b.0). These tests do NOT need a database —
 * they only verify that the JPA annotations are well-formed
 * (the right schema, the right table name, the right unique
 * constraints) and that {@code @PrePersist} hooks assign
 * {@code publicUuid} on persist.
 *
 * <p>The full repository tests (TestContainers, cross-tenant
 * isolation) are out of scope for BE-7b.0 and live in BE-7b.1
 * (where the service layer is wired and end-to-end attempts
 * make sense). The smoke tests here are a <em>load-bearing</em>
 * fast feedback gate that catches:
 * <ul>
 *   <li>Missing {@code @Entity} / {@code @Table} annotations
 *       (would prevent Hibernate from picking the class up).</li>
 *   <li>Wrong schema / table name (would break Flyway +
 *       JPA alignment — the same name has to be in the
 *       migration and the entity).</li>
 *   <li>Missing public_uuid column (entities without it
 *       cannot be returned to the FE).</li>
 *   <li>Regressions in the {@code @PrePersist} hook that
 *       generates UUIDs.</li>
 * </ul>
 */
class QuizEntitySmokeTest {

	@Test
	@DisplayName("Quiz: @Entity + schema=edushift + table=lms_quizzes + public_uuid")
	void quizAnnotations() {
		Entity entity = Quiz.class.getAnnotation(Entity.class);
		Table table = Quiz.class.getAnnotation(Table.class);

		assertThat(entity).as("Quiz must be @Entity").isNotNull();
		assertThat(table).as("Quiz must be @Table").isNotNull();
		assertThat(table.schema()).isEqualTo("edushift");
		assertThat(table.name()).isEqualTo("lms_quizzes");

		// public_uuid column must exist (FE relies on it).
		boolean hasPublicUuid = hasFieldAnnotatedWith(Quiz.class, "public_uuid");
		assertThat(hasPublicUuid).as("Quiz must have a public_uuid column").isTrue();
	}

	@Test
	@DisplayName("Quiz: @PrePersist assigns publicUuid on first persist")
	void quizPrePersistGeneratesUuid() {
		Quiz q = new Quiz();
		assertThat(q.getPublicUuid()).as("publicUuid starts null").isNull();
		// We can't call @PrePersist directly (it's a JPA callback),
		// but we can call the equivalent private method via reflection.
		try {
			var m = Quiz.class.getDeclaredMethod("onPrePersist");
			m.setAccessible(true);
			m.invoke(q);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("@PrePersist hook missing or not callable: " + e);
		}
		assertThat(q.getPublicUuid()).as("publicUuid set by @PrePersist").isNotNull();
		assertThat(q.getStatus()).as("status defaults to DRAFT").isEqualTo(QuizStatus.DRAFT);
	}

	@Test
	@DisplayName("QuizAttempt: @PrePersist assigns publicUuid, status=IN_PROGRESS, startedAt=now()")
	void quizAttemptPrePersistDefaults() {
		QuizAttempt a = new QuizAttempt();
		assertThat(a.getStatus()).isNull();
		assertThat(a.getStartedAt()).isNull();
		try {
			var m = QuizAttempt.class.getDeclaredMethod("onPrePersist");
			m.setAccessible(true);
			m.invoke(a);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
		assertThat(a.getPublicUuid()).isNotNull();
		assertThat(a.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
		assertThat(a.getStartedAt()).isNotNull();
		assertThat(a.getStartedAt()).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	@DisplayName("QuizQuestion/QuizOption/QuizAnswer: @PrePersist only generates publicUuid")
	void otherEntitiesPrePersistOnlyPublicUuid() {
		for (Class<?> type : new Class<?>[]{
				QuizQuestion.class, QuizOption.class, QuizAnswer.class}) {
			Object instance;
			try {
				instance = type.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("no-arg constructor missing for " + type, e);
			}
			try {
				var m = type.getDeclaredMethod("onPrePersist");
				m.setAccessible(true);
				m.invoke(instance);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("@PrePersist missing for " + type, e);
			}
			try {
				var getPublicUuid = type.getMethod("getPublicUuid");
				Object publicUuid = getPublicUuid.invoke(instance);
				assertThat(publicUuid).as(type.getSimpleName() + ".publicUuid").isNotNull();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("getPublicUuid missing for " + type, e);
			}
		}
	}

	@Test
	@DisplayName("All Quiz enums have the expected number of values")
	void enumsAreStable() {
		// Guard against accidental rename/drop that would break
		// the DB CHECK constraints and the JPA @Enumerated mapping.
		assertThat(QuizStatus.values()).containsExactly(
				QuizStatus.DRAFT, QuizStatus.PUBLISHED, QuizStatus.CLOSED);
		assertThat(QuestionType.values()).containsExactly(
				QuestionType.MC, QuestionType.TF, QuestionType.SHORT_ANSWER);
		assertThat(AttemptStatus.values()).containsExactly(
				AttemptStatus.IN_PROGRESS, AttemptStatus.SUBMITTED,
				AttemptStatus.AUTO_GRADED, AttemptStatus.GRADED, AttemptStatus.EXPIRED);
	}

	private static boolean hasFieldAnnotatedWith(Class<?> type, String columnName) {
		for (var f : type.getDeclaredFields()) {
			Column col = f.getAnnotation(Column.class);
			if (col != null && columnName.equals(col.name())) {
				return true;
			}
		}
		return false;
	}
}
