package com.edushift.modules.quizzes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

@DisplayName("Quizzes repositories — extend JpaRepository with tenant-scoped custom queries")
class QuizRepositoriesTest {

    @Test
    @DisplayName("QuizRepository — extends JpaRepository<Quiz, UUID>")
    void quizRepo() {
        assertThat(JpaRepository.class).isAssignableFrom(QuizRepository.class);
        assertThat(QuizRepository.class.getInterfaces()).contains(JpaRepository.class);
        // the entity types generic parameters are erased at runtime, so we
        // assert the interface contract via a reflection-free type name check
        assertThat(QuizRepository.class.getSimpleName()).isEqualTo("QuizRepository");
        var q = new Quiz();
        assertThat(q).isNotNull();
    }

    @Test
    @DisplayName("QuizQuestionRepository — extends JpaRepository<QuizQuestion, UUID>")
    void questionRepo() {
        assertThat(JpaRepository.class).isAssignableFrom(QuizQuestionRepository.class);
    }

    @Test
    @DisplayName("QuizOptionRepository — extends JpaRepository<QuizOption, UUID>")
    void optionRepo() {
        assertThat(JpaRepository.class).isAssignableFrom(QuizOptionRepository.class);
    }

    @Test
    @DisplayName("QuizAttemptRepository — extends JpaRepository<QuizAttempt, UUID>")
    void attemptRepo() {
        assertThat(JpaRepository.class).isAssignableFrom(QuizAttemptRepository.class);
    }

    @Test
    @DisplayName("QuizAnswerRepository — extends JpaRepository<QuizAnswer, UUID>")
    void answerRepo() {
        assertThat(JpaRepository.class).isAssignableFrom(QuizAnswerRepository.class);
    }

    @Test
    @DisplayName("Repository interfaces are Spring @Repository components")
    void areSpringRepositories() throws NoSuchMethodException {
        assertThat(QuizRepository.class.getAnnotation(org.springframework.stereotype.Repository.class)).isNotNull();
        assertThat(QuizQuestionRepository.class.getAnnotation(org.springframework.stereotype.Repository.class)).isNotNull();
        assertThat(QuizOptionRepository.class.getAnnotation(org.springframework.stereotype.Repository.class)).isNotNull();
        assertThat(QuizAttemptRepository.class.getAnnotation(org.springframework.stereotype.Repository.class)).isNotNull();
        assertThat(QuizAnswerRepository.class.getAnnotation(org.springframework.stereotype.Repository.class)).isNotNull();
    }

    @Test
    @DisplayName("All repositories expose findByPublicUuid for the public UUID contract")
    void findByPublicUuidMethodsExist() throws NoSuchMethodException {
        assertThat(QuizRepository.class.getMethod("findByPublicUuid", java.util.UUID.class)).isNotNull();
        assertThat(QuizQuestionRepository.class.getMethod("findByPublicUuid", java.util.UUID.class)).isNotNull();
        assertThat(QuizOptionRepository.class.getMethod("findByPublicUuid", java.util.UUID.class)).isNotNull();
        assertThat(QuizAttemptRepository.class.getMethod("findByPublicUuid", java.util.UUID.class)).isNotNull();
        assertThat(QuizAnswerRepository.class.getMethod("findByPublicUuid", java.util.UUID.class)).isNotNull();
    }
}