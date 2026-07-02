package com.edushift.modules.evaluations.evaluationrubric.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttachRubricRequestTest {

    @Test
    @DisplayName("constructor + accessors")
    void accessors() {
        var req = new AttachRubricRequest("11111111-2222-3333-4444-555555555555");
        assertThat(req.rubricPublicUuid())
                .isEqualTo("11111111-2222-3333-4444-555555555555");
    }

    @Test
    @DisplayName("null payload is representable")
    void nullPayload() {
        var req = new AttachRubricRequest(null);
        assertThat(req.rubricPublicUuid()).isNull();
    }
}