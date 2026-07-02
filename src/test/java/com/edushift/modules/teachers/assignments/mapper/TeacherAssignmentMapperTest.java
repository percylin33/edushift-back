package com.edushift.modules.teachers.assignments.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeacherAssignmentMapperTest {

    private Teacher teacher() {
        var t = new Teacher();
        t.setPublicUuid(UUID.randomUUID());
        t.setFirstName("Ana");
        t.setLastName("Diaz");
        return t;
    }

    private Section section() {
        var s = new Section();
        s.setPublicUuid(UUID.randomUUID());
        s.setName("Section A");
        return s;
    }

    private Course course() {
        var c = new Course();
        c.setPublicUuid(UUID.randomUUID());
        c.setCode("MAT");
        c.setName("Matemática");
        return c;
    }

    private AcademicPeriod period() {
        var p = new AcademicPeriod();
        p.setPublicUuid(UUID.randomUUID());
        p.setPeriodType(PeriodType.BIMESTRE);
        p.setOrdinal(1);
        p.setName("B1 2026");
        var year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");
        p.setAcademicYear(year);
        return p;
    }

    @Test
    @DisplayName("toResponse: full denormalised projection + active flag")
    void toResponse() {
        var a = new TeacherAssignment();
        a.setPublicUuid(UUID.randomUUID());
        a.setTeacher(teacher());
        a.setSection(section());
        a.setCourse(course());
        a.setAcademicPeriod(period());
        a.setAssignedAt(Instant.parse("2026-03-01T00:00:00Z"));
        a.setUnassignedAt(null);
        a.setNotes("notes");
        a.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        a.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));

        var mapper = new TeacherAssignmentMapper();
        var resp = mapper.toResponse(a);
        assertThat(resp.publicUuid()).isEqualTo(a.getPublicUuid());
        assertThat(resp.teacherFullName()).isEqualTo("Ana Diaz");
        assertThat(resp.sectionName()).isEqualTo("Section A");
        assertThat(resp.courseCode()).isEqualTo("MAT");
        assertThat(resp.periodName()).isEqualTo("B1 2026");
        assertThat(resp.active()).isTrue();
        assertThat(resp.unassignedAt()).isNull();
    }

    @Test
    @DisplayName("toListItem: drops audit timestamps")
    void toListItem() {
        var a = new TeacherAssignment();
        a.setPublicUuid(UUID.randomUUID());
        a.setTeacher(teacher());
        a.setSection(section());
        a.setCourse(course());
        a.setAcademicPeriod(period());
        a.setAssignedAt(Instant.now());

        var item = new TeacherAssignmentMapper().toListItem(a);
        assertThat(item.courseCode()).isEqualTo("MAT");
        assertThat(item.active()).isTrue();
    }

    @Test
    @DisplayName("toSectionTeacherItem: reverse-view projection")
    void toSectionTeacherItem() {
        var t = teacher();
        t.setEmail("ana@acme.test");
        var a = new TeacherAssignment();
        a.setPublicUuid(UUID.randomUUID());
        a.setTeacher(t);
        a.setSection(section());
        a.setCourse(course());
        a.setAcademicPeriod(period());
        a.setAssignedAt(Instant.now());

        var item = new TeacherAssignmentMapper().toSectionTeacherItem(a);
        assertThat(item.teacherEmail()).isEqualTo("ana@acme.test");
        assertThat(item.courseCode()).isEqualTo("MAT");
        assertThat(item.periodOrdinal()).isEqualTo(1);
    }
}