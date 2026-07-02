package com.edushift.modules.evaluations.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationErrorCodesTest {

    @Test
    @DisplayName("error codes are stable constants")
    void codesAreStable() {
        assertThat(EvaluationErrorCodes.EVAL_NAME_EXISTS).isEqualTo("EVAL_NAME_EXISTS");
        assertThat(EvaluationErrorCodes.EVAL_DATE_INVERTED).isEqualTo("EVAL_DATE_INVERTED");
        assertThat(EvaluationErrorCodes.EVAL_KIND_SCALE_MISMATCH).isEqualTo("EVAL_KIND_SCALE_MISMATCH");
        assertThat(EvaluationErrorCodes.EVAL_NOT_EDITABLE).isEqualTo("EVAL_NOT_EDITABLE");
        assertThat(EvaluationErrorCodes.EVAL_CLOSED).isEqualTo("EVAL_CLOSED");
        assertThat(EvaluationErrorCodes.EVAL_NOT_IN_ASSIGNMENT).isEqualTo("EVAL_NOT_IN_ASSIGNMENT");
        assertThat(EvaluationErrorCodes.EVAL_UNIT_NOT_IN_COURSE).isEqualTo("EVAL_UNIT_NOT_IN_COURSE");
        assertThat(EvaluationErrorCodes.EVAL_SESSION_NOT_IN_ASSIGNMENT)
                .isEqualTo("EVAL_SESSION_NOT_IN_ASSIGNMENT");
        assertThat(EvaluationErrorCodes.EVAL_HAS_GRADES).isEqualTo("EVAL_HAS_GRADES");
        assertThat(EvaluationErrorCodes.EVAL_ILLEGAL_TRANSITION).isEqualTo("EVAL_ILLEGAL_TRANSITION");
        assertThat(EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET).isEqualTo("EVAL_RUBRIC_NOT_SET");
    }

    @Test
    @DisplayName("all codes are unique")
    void allCodesAreUnique() {
        var codes = java.util.List.of(
                EvaluationErrorCodes.EVAL_NAME_EXISTS,
                EvaluationErrorCodes.EVAL_DATE_INVERTED,
                EvaluationErrorCodes.EVAL_KIND_SCALE_MISMATCH,
                EvaluationErrorCodes.EVAL_NOT_EDITABLE,
                EvaluationErrorCodes.EVAL_CLOSED,
                EvaluationErrorCodes.EVAL_NOT_IN_ASSIGNMENT,
                EvaluationErrorCodes.EVAL_UNIT_NOT_IN_COURSE,
                EvaluationErrorCodes.EVAL_SESSION_NOT_IN_ASSIGNMENT,
                EvaluationErrorCodes.EVAL_HAS_GRADES,
                EvaluationErrorCodes.EVAL_ILLEGAL_TRANSITION,
                EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET);
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("class is not instantiable")
    void notInstantiable() throws Exception {
        var ctor = EvaluationErrorCodes.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.getModifiers())
                .matches(java.lang.reflect.Modifier::isPrivate);
    }
}