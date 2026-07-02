package com.edushift.modules.evaluations.evaluationrubric.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationRubricEntityTest {

    @Test
    @DisplayName("setters + getters")
    void accessors() throws Exception {
        var er = new EvaluationRubric();
        Evaluation ev = new Evaluation();
        Rubric rb = new Rubric();
        setField(rb, "publicUuid", UUID.randomUUID());
        setField(ev, "publicUuid", UUID.randomUUID());
        er.setEvaluation(ev);
        er.setRubric(rb);
        assertThat(er.getEvaluation()).isSameAs(ev);
        assertThat(er.getRubric()).isSameAs(rb);
    }

    @Test
    @DisplayName("equals + hashCode use the internal id")
    void equalsHashCode() throws Exception {
        var er1 = new EvaluationRubric();
        var er2 = new EvaluationRubric();
        var id = UUID.randomUUID();
        setField(er1, "id", id);
        setField(er2, "id", id);
        assertThat(er1).isEqualTo(er2);
        assertThat(er1.hashCode()).isEqualTo(er2.hashCode());

        var er3 = new EvaluationRubric();
        setField(er3, "id", UUID.randomUUID());
        assertThat(er1).isNotEqualTo(er3);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getSuperclass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}