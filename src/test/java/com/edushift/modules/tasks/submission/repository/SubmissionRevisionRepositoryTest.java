package com.edushift.modules.tasks.submission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionRevision;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Verifies the Spring Data query signatures exposed by
 * {@link SubmissionRevisionRepository}, including the @Query for
 * {@code findMaxRevisionNumber}.
 */
@DisplayName("SubmissionRevisionRepository contract")
class SubmissionRevisionRepositoryTest {

    @Test
    @DisplayName("extends JpaRepository<SubmissionRevision, UUID>")
    void extendsJpaRepository() {
        assertThat(JpaRepository.class.isAssignableFrom(SubmissionRevisionRepository.class)).isTrue();
    }

    @Test
    @DisplayName("declares findMaxRevisionNumber with @Query")
    void findMaxRevisionNumberSignature() throws NoSuchMethodException {
        Method m = SubmissionRevisionRepository.class.getMethod(
                "findMaxRevisionNumber", Submission.class);

        assertThat(m.getReturnType()).isEqualTo(Short.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        Query q = m.getAnnotation(Query.class);
        assertThat(q.value()).contains("max(r.revisionNumber)");
        assertThat(q.value()).contains(":submission");

        // Parameter annotated with @Param("submission")
        var paramAnnotations = m.getParameters()[0].getAnnotations();
        assertThat(paramAnnotations).anyMatch(a -> a.annotationType().equals(Param.class)
                && ((Param) a).value().equals("submission"));
    }

    @Test
    @DisplayName("findMaxRevisionNumber — returns Short from mock")
    void findMaxRevisionNumberMock() {
        SubmissionRevisionRepository repo =
                org.mockito.Mockito.mock(SubmissionRevisionRepository.class);
        Submission s = new Submission();
        org.mockito.Mockito.when(repo.findMaxRevisionNumber(s)).thenReturn((short) 7);

        assertThat(repo.findMaxRevisionNumber(s)).isEqualTo((short) 7);
    }

    @Test
    @DisplayName("findMaxRevisionNumber — null when no revisions yet")
    void findMaxRevisionNumberNull() {
        SubmissionRevisionRepository repo =
                org.mockito.Mockito.mock(SubmissionRevisionRepository.class);
        Submission s = new Submission();
        org.mockito.Mockito.when(repo.findMaxRevisionNumber(s)).thenReturn(null);

        assertThat(repo.findMaxRevisionNumber(s)).isNull();
    }
}