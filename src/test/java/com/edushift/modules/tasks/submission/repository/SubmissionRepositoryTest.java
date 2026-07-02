package com.edushift.modules.tasks.submission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.submission.entity.Submission;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Verifies the Spring Data derived/custom query signatures exposed by
 * {@link SubmissionRepository}.
 */
@DisplayName("SubmissionRepository contract")
class SubmissionRepositoryTest {

    @Test
    @DisplayName("extends JpaRepository<Submission, UUID>")
    void extendsJpaRepository() {
        assertThat(JpaRepository.class.isAssignableFrom(SubmissionRepository.class)).isTrue();
    }

    @Test
    @DisplayName("declares findByPublicUuid")
    void findByPublicUuid() throws NoSuchMethodException {
        Method m = SubmissionRepository.class.getMethod("findByPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isEqualTo(Optional.class);

        SubmissionRepository repo = org.mockito.Mockito.mock(SubmissionRepository.class);
        UUID id = UUID.randomUUID();
        Submission s = new Submission();
        org.mockito.Mockito.when(repo.findByPublicUuid(id)).thenReturn(Optional.of(s));

        assertThat(repo.findByPublicUuid(id)).contains(s);
    }

    @Test
    @DisplayName("declares findByTaskAndStudentUserId")
    void findByTaskAndStudent() throws NoSuchMethodException {
        Method m = SubmissionRepository.class.getMethod(
                "findByTaskAndStudentUserId", Task.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(Optional.class);

        SubmissionRepository repo = org.mockito.Mockito.mock(SubmissionRepository.class);
        Task t = new Task();
        UUID student = UUID.randomUUID();
        Submission s = new Submission();
        org.mockito.Mockito.when(repo.findByTaskAndStudentUserId(t, student))
                .thenReturn(Optional.of(s));

        assertThat(repo.findByTaskAndStudentUserId(t, student)).contains(s);
    }

    @Test
    @DisplayName("declares findAllByTaskOrderByCreatedAtDesc")
    void findAllByTask() throws NoSuchMethodException {
        Method m = SubmissionRepository.class.getMethod(
                "findAllByTaskOrderByCreatedAtDesc", Task.class, Pageable.class);
        assertThat(m.getReturnType()).isEqualTo(Page.class);

        SubmissionRepository repo = org.mockito.Mockito.mock(SubmissionRepository.class);
        Task t = new Task();
        Pageable p = Pageable.ofSize(20);
        @SuppressWarnings("unchecked")
        Page<Submission> page = (Page<Submission>) org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(repo.findAllByTaskOrderByCreatedAtDesc(t, p))
                .thenReturn(page);

        assertThat(repo.findAllByTaskOrderByCreatedAtDesc(t, p)).isSameAs(page);
    }

    @Test
    @DisplayName("declares findAllByAttachmentPublicUuid (returns List)")
    void findAllByAttachment() throws NoSuchMethodException {
        Method m = SubmissionRepository.class.getMethod(
                "findAllByAttachmentPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isEqualTo(List.class);

        SubmissionRepository repo = org.mockito.Mockito.mock(SubmissionRepository.class);
        UUID attach = UUID.randomUUID();
        Submission s = new Submission();
        org.mockito.Mockito.when(repo.findAllByAttachmentPublicUuid(attach))
                .thenReturn(List.of(s));

        assertThat(repo.findAllByAttachmentPublicUuid(attach)).containsExactly(s);
    }

    @Test
    @DisplayName("findByPublicUuid missing — empty Optional")
    void findMissing() {
        SubmissionRepository repo = org.mockito.Mockito.mock(SubmissionRepository.class);
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.when(repo.findByPublicUuid(id)).thenReturn(Optional.empty());

        assertThat(repo.findByPublicUuid(id)).isEmpty();
    }
}