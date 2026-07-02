package com.edushift.modules.evaluations.rubric.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RubricDtoTest {

    @Test
    @DisplayName("DescriptorInput + DescriptorView — accessors")
    void descriptor() {
        var in = new DescriptorInput("EN_INICIO", "Needs improvement");
        var view = new DescriptorView("EN_INICIO", "Needs improvement");
        assertThat(in.level()).isEqualTo("EN_INICIO");
        assertThat(in.text()).isEqualTo("Needs improvement");
        assertThat(view.level()).isEqualTo("EN_INICIO");
        assertThat(view.text()).isEqualTo("Needs improvement");
    }

    @Test
    @DisplayName("LevelInput + LevelView — accessors")
    void level() {
        var in = new LevelInput("EN_INICIO", "En inicio", 1);
        var view = new LevelView("EN_INICIO", "En inicio", 1);
        assertThat(in.code()).isEqualTo("EN_INICIO");
        assertThat(in.name()).isEqualTo("En inicio");
        assertThat(in.order()).isEqualTo(1);
        assertThat(view.order()).isEqualTo(1);
    }

    @Test
    @DisplayName("CriterionInput + CriterionView — accessors")
    void criterion() {
        var desc = new DescriptorInput("A", "Achieved");
        var in = new CriterionInput("clarity", "Clarity",
                "Description", new BigDecimal("25.00"), List.of(desc));
        var view = new CriterionView("clarity", "Clarity", "Description",
                new BigDecimal("25.00"),
                List.of(new DescriptorView("A", "Achieved")));
        assertThat(in.key()).isEqualTo("clarity");
        assertThat(in.weight()).isEqualByComparingTo("25.00");
        assertThat(in.descriptors()).hasSize(1);
        assertThat(view.descriptors()).hasSize(1);
        assertThat(view.descriptors().get(0).level()).isEqualTo("A");
    }

    @Test
    @DisplayName("CreateRubricRequest — accessors")
    void createRequest() {
        var criterion = new CriterionInput("k", "n", null,
                new BigDecimal("100.00"), List.of());
        var level = new LevelInput("A", "A", 1);
        var req = new CreateRubricRequest("My Rubric", "Desc",
                List.of(criterion), List.of(level));
        assertThat(req.name()).isEqualTo("My Rubric");
        assertThat(req.description()).isEqualTo("Desc");
        assertThat(req.criteria()).hasSize(1);
        assertThat(req.levels()).hasSize(1);
    }

    @Test
    @DisplayName("UpdateRubricRequest — isEmpty")
    void updateIsEmpty() {
        assertThat(new UpdateRubricRequest(null, null, null, null).isEmpty()).isTrue();
        var nonEmpty = new UpdateRubricRequest("rename", null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("rename");
    }

    @Test
    @DisplayName("RubricFilters — accessors")
    void filters() {
        var f = new RubricFilters(Boolean.TRUE, Boolean.FALSE, "math");
        assertThat(f.systemOnly()).isTrue();
        assertThat(f.isActive()).isFalse();
        assertThat(f.q()).isEqualTo("math");
    }

    @Test
    @DisplayName("RubricListItem — accessors")
    void listItem() {
        var item = new RubricListItem(
                UUID.randomUUID(), "My Rubric", "Desc",
                Boolean.TRUE, UUID.randomUUID(), 5,
                List.of("Clarity (25%)", "Cohesion (25%)"),
                Boolean.TRUE,
                Instant.now(), Instant.now());
        assertThat(item.criterionCount()).isEqualTo(5);
        assertThat(item.criterionSummary()).hasSize(2);
        assertThat(item.isSystem()).isTrue();
        assertThat(item.parentRubricPublicUuid()).isNotNull();
    }

    @Test
    @DisplayName("RubricResponse — accessors")
    void response() {
        UUID puuid = UUID.randomUUID();
        UUID parentUuid = UUID.randomUUID();
        Instant t = Instant.now();
        var resp = new RubricResponse(
                puuid, "My Rubric", "Desc",
                List.of(), List.of(), Boolean.FALSE,
                parentUuid, Boolean.TRUE,
                t, t);
        assertThat(resp.publicUuid()).isEqualTo(puuid);
        assertThat(resp.parentRubricPublicUuid()).isEqualTo(parentUuid);
        assertThat(resp.isSystem()).isFalse();
        assertThat(resp.isActive()).isTrue();
    }
}