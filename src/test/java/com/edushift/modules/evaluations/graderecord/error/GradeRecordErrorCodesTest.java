package com.edushift.modules.evaluations.graderecord.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeRecordErrorCodesTest {

    @Test
    @DisplayName("error codes are stable constants")
    void codesAreStable() {
        assertThat(GradeRecordErrorCodes.GRADE_VALUE_REQUIRED).isEqualTo("GRADE_VALUE_REQUIRED");
        assertThat(GradeRecordErrorCodes.GRADE_SCORE_OUT_OF_RANGE).isEqualTo("GRADE_SCORE_OUT_OF_RANGE");
        assertThat(GradeRecordErrorCodes.GRADE_LITERAL_INVALID).isEqualTo("GRADE_LITERAL_INVALID");
        assertThat(GradeRecordErrorCodes.GRADE_EVAL_CLOSED).isEqualTo("GRADE_EVAL_CLOSED");
        assertThat(GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED).isEqualTo("GRADE_STUDENT_NOT_ENROLLED");
        assertThat(GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW).isEqualTo("GRADE_BULK_INVALID_ROW");
        assertThat(GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH).isEqualTo("GRADE_VALUE_SHAPE_MISMATCH");
    }

    @Test
    @DisplayName("all codes are unique")
    void allCodesAreUnique() {
        var codes = java.util.List.of(
                GradeRecordErrorCodes.GRADE_VALUE_REQUIRED,
                GradeRecordErrorCodes.GRADE_SCORE_OUT_OF_RANGE,
                GradeRecordErrorCodes.GRADE_LITERAL_INVALID,
                GradeRecordErrorCodes.GRADE_EVAL_CLOSED,
                GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED,
                GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW,
                GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH);
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("class is not instantiable")
    void notInstantiable() throws Exception {
        var ctor = GradeRecordErrorCodes.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.getModifiers())
                .matches(java.lang.reflect.Modifier::isPrivate);
    }
}