package com.edushift.modules.teachers.assignments.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.entity.Teacher;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeacherAssignmentRepositoryInterfaceTest {

    @Test
    @DisplayName("findByPublicUuid returns Optional")
    void findByPublicUuid() throws Exception {
        assertThat(TeacherAssignmentRepository.class.getMethod("findByPublicUuid", UUID.class)
                .getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findActiveTuple returns Optional")
    void findActiveTuple() throws Exception {
        Method m = TeacherAssignmentRepository.class.getMethod("findActiveTuple",
                Teacher.class, Section.class, Course.class, AcademicPeriod.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findAllByTeacher + findAllBySectionActive return List")
    void list() throws Exception {
        assertThat(TeacherAssignmentRepository.class.getMethod("findAllByTeacher",
                Teacher.class, AcademicPeriod.class, boolean.class)
                .getReturnType()).isAssignableFrom(List.class);
        assertThat(TeacherAssignmentRepository.class.getMethod("findAllBySectionActive",
                Section.class, AcademicPeriod.class)
                .getReturnType()).isAssignableFrom(List.class);
    }

    @Test
    @DisplayName("existsActiveByTeacher / existsActiveByCourse / existsActiveByPeriod return boolean")
    void existsActive() throws Exception {
        assertThat(TeacherAssignmentRepository.class.getMethod("existsActiveByTeacher", Teacher.class)
                .getReturnType()).isEqualTo(boolean.class);
        assertThat(TeacherAssignmentRepository.class.getMethod("existsActiveByCourse", Course.class)
                .getReturnType()).isEqualTo(boolean.class);
        assertThat(TeacherAssignmentRepository.class.getMethod("existsActiveByPeriod", AcademicPeriod.class)
                .getReturnType()).isEqualTo(boolean.class);
    }
}