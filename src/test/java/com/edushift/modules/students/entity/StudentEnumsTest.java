package com.edushift.modules.students.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StudentEnumsTest {

    @Nested
    @DisplayName("DocumentType")
    class DocumentTypeEnum {

        @Test
        @DisplayName("values are the 4 known types + fromName parsing")
        void surface() {
            assertThat(DocumentType.values()).hasSize(4);
            assertThat(DocumentType.fromName("DNI")).isEqualTo(DocumentType.DNI);
            assertThat(DocumentType.fromName("PASSPORT")).isEqualTo(DocumentType.PASSPORT);
            assertThat(DocumentType.fromName(null)).isNull();
            assertThat(DocumentType.fromName("X")).isNull();
        }
    }

    @Nested
    @DisplayName("Gender")
    class GenderEnum {

        @Test
        @DisplayName("values + fromName")
        void surface() {
            assertThat(Gender.values()).hasSize(4);
            assertThat(Gender.fromName("FEMALE")).isEqualTo(Gender.FEMALE);
            assertThat(Gender.fromName(null)).isNull();
        }
    }

    @Nested
    @DisplayName("EnrollmentStatus")
    class EnrollmentStatusEnum {

        @Test
        @DisplayName("values + fromName")
        void surface() {
            assertThat(EnrollmentStatus.values()).hasSize(5);
            assertThat(EnrollmentStatus.fromName("ENROLLED")).isEqualTo(EnrollmentStatus.ENROLLED);
            assertThat(EnrollmentStatus.fromName(null)).isNull();
            assertThat(EnrollmentStatus.fromName("X")).isNull();
        }
    }

    @Nested
    @DisplayName("RelationshipType")
    class RelationshipEnum {

        @Test
        @DisplayName("values + fromName")
        void surface() {
            assertThat(RelationshipType.values()).hasSize(5);
            assertThat(RelationshipType.fromName("MOTHER")).isEqualTo(RelationshipType.MOTHER);
            assertThat(RelationshipType.fromName(null)).isNull();
        }
    }
}