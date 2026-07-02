package com.edushift.modules.teachers.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class TeachersRepositoriesInterfaceTest {

    @Test
    @DisplayName("TeacherRepository: findByPublicUuid / findByDocument / findByEmailIgnoreCase / findByUserId")
    void teacherRepo() throws Exception {
        Class<?> repo = TeacherRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByDocument", DocumentType.class, String.class)
                .getReturnType()).isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByEmailIgnoreCase", String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByUserId", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("TeacherRepository.findFiltered returns Page<Teacher>")
    void findFiltered() throws Exception {
        Method m = TeacherRepository.class.getMethod("findFiltered",
                String.class, EmploymentStatus.class, Boolean.class, Pageable.class);
        assertThat(m.getReturnType()).isAssignableFrom(Page.class);
    }
}