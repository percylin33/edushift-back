package com.edushift.modules.academic.levelgrade.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LevelGradeEntityTest {

    @Test
    @DisplayName("AcademicLevel setters and getters")
    void academicLevel() {
        var l = new AcademicLevel();
        l.setCode("PRIMARIA");
        l.setName("Primaria");
        l.setOrdinal(2);

        assertThat(l.getCode()).isEqualTo("PRIMARIA");
        assertThat(l.getOrdinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("Grade setters and getters")
    void grade() {
        var level = new AcademicLevel();
        level.setCode("PRIMARIA");

        var g = new Grade();
        g.setLevel(level);
        g.setName("1ro");
        g.setOrdinal(1);

        assertThat(g.getName()).isEqualTo("1ro");
        assertThat(g.getLevel().getCode()).isEqualTo("PRIMARIA");
    }
}
