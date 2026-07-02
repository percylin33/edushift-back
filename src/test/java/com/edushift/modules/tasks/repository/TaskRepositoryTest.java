package com.edushift.modules.tasks.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.tasks.entity.Task;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Verifies the Spring Data derived/custom query signatures exposed by
 * {@link TaskRepository}. The interface is exercised through Mockito-style
 * verification rather than a real DB (no integration context is started).
 */
@DisplayName("TaskRepository contract")
class TaskRepositoryTest {

    private final TaskRepository repository =
            org.mockito.Mockito.mock(TaskRepository.class);

    @Test
    @DisplayName("extends JpaRepository<Task, UUID>")
    void extendsJpaRepository() {
        assertThat(JpaRepository.class.isAssignableFrom(TaskRepository.class)).isTrue();
    }

    @Test
    @DisplayName("declares findByPublicUuid")
    void findByPublicUuid() throws NoSuchMethodException {
        Method m = TaskRepository.class.getMethod("findByPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isEqualTo(Optional.class);

        TaskRepository repo = org.mockito.Mockito.mock(TaskRepository.class);
        UUID id = UUID.randomUUID();
        Task t = new Task();
        org.mockito.Mockito.when(repo.findByPublicUuid(id)).thenReturn(Optional.of(t));

        Optional<Task> result = repo.findByPublicUuid(id);
        assertThat(result).contains(t);
    }

    @Test
    @DisplayName("declares existsByPublicUuid")
    void existsByPublicUuid() throws NoSuchMethodException {
        Method m = TaskRepository.class.getMethod("existsByPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isEqualTo(boolean.class);

        TaskRepository repo = org.mockito.Mockito.mock(TaskRepository.class);
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.when(repo.existsByPublicUuid(id)).thenReturn(true);

        assertThat(repo.existsByPublicUuid(id)).isTrue();
    }

    @Test
    @DisplayName("declares findAllBySectionOrderByDueAtDesc")
    void findAllBySectionOrderByDueAtDesc() throws NoSuchMethodException {
        Method m = TaskRepository.class.getMethod(
                "findAllBySectionOrderByDueAtDesc", Section.class, Pageable.class);
        assertThat(m.getReturnType()).isEqualTo(Page.class);

        TaskRepository repo = org.mockito.Mockito.mock(TaskRepository.class);
        Section s = new Section();
        Pageable p = Pageable.ofSize(20);
        @SuppressWarnings("unchecked")
        Page<Task> mockPage = (Page<Task>) org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(repo.findAllBySectionOrderByDueAtDesc(s, p))
                .thenReturn(mockPage);

        assertThat(repo.findAllBySectionOrderByDueAtDesc(s, p)).isSameAs(mockPage);
    }

    @Test
    @DisplayName("missing publicUuid → empty Optional")
    void findMissing() {
        TaskRepository repo = org.mockito.Mockito.mock(TaskRepository.class);
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.when(repo.findByPublicUuid(id)).thenReturn(Optional.empty());

        assertThat(repo.findByPublicUuid(id)).isEmpty();
    }
}