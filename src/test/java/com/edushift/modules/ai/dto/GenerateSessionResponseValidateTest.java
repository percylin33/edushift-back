package com.edushift.modules.ai.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.ai.exception.AiParseException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerateSessionResponseValidateTest {

    @Test
    @DisplayName("accepts a well-formed session whose activity durations sum to expected")
    void valid() {
        var a1 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio", 5, "Dinamica de arranque");
        var a2 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.DESARROLLO, "Desarrollo", 40, "Trabajo en clase");
        var a3 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.CIERRE, "Cierre", 15, "Reflexion final");
        var res = new GenerateSessionResponse.Resource(
                GenerateSessionResponse.Resource.Type.TEXT, "Lectura", null, "Texto guia");
        var crit = new GenerateSessionResponse.EvaluationCriterion("Criterio 1", 1.0,
                "Cumple con la tarea");
        var resp = new GenerateSessionResponse("La revolucion francesa", "Una mirada al periodo",
                List.of(a1, a2, a3), List.of(res), List.of(crit), List.of(), List.of());
        assertThatCode(() -> resp.validate("raw", 60)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects when sum of activity durations doesn't match expectedDuration")
    void durationMismatch() {
        var a1 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio", 5, "x".repeat(20));
        var a2 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.DESARROLLO, "Desarrollo", 20, "y".repeat(20));
        var a3 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.CIERRE, "Cierre", 5, "z".repeat(20));
        var resp = new GenerateSessionResponse("La revolucion francesa", "Una mirada al periodo",
                List.of(a1, a2, a3), null, null, List.of(), List.of());
        assertThatThrownBy(() -> resp.validate("raw", 60))
                .isInstanceOf(AiParseException.class)
                .hasMessageContaining("sum of activities.durationMinutes")
                .hasMessageContaining("must equal requested durationMinutes");
    }

    @Test
    @DisplayName("rejects when no activity covers each phase")
    void missingPhase() {
        var a1 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio", 20, "x".repeat(20));
        var a2 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio 2", 30, "y".repeat(20));
        var a3 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio 3", 10, "z".repeat(20));
        var resp = new GenerateSessionResponse("La revolucion francesa", "Una mirada al periodo",
                List.of(a1, a2, a3), null, null, List.of(), List.of());
        assertThatThrownBy(() -> resp.validate("raw", 60))
                .isInstanceOf(AiParseException.class)
                .hasMessageContaining("must include at least 1 of each phase");
    }

    @Test
    @DisplayName("rejects when evaluationCriteria weights do not sum to 1.0")
    void weightSum() {
        var a1 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.INICIO, "Inicio", 10, "x".repeat(20));
        var a2 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.DESARROLLO, "Desarrollo", 30, "y".repeat(20));
        var a3 = new GenerateSessionResponse.Activity(
                GenerateSessionResponse.Activity.Phase.CIERRE, "Cierre", 20, "z".repeat(20));
        var res = new GenerateSessionResponse.Resource(
                GenerateSessionResponse.Resource.Type.TEXT, "Lectura", null, "Texto guia");
        var crit1 = new GenerateSessionResponse.EvaluationCriterion("Criterio 1", 0.3, "descripcion");
        var crit2 = new GenerateSessionResponse.EvaluationCriterion("Criterio 2", 0.3, "descripcion");
        var resp = new GenerateSessionResponse("La revolucion francesa", "Una mirada al periodo",
                List.of(a1, a2, a3), List.of(res), List.of(crit1, crit2), List.of(), List.of());
        assertThatThrownBy(() -> resp.validate("raw", 60))
                .isInstanceOf(AiParseException.class)
                .hasMessageContaining("sum of evaluationCriteria.weight must be 1.0");
    }
}