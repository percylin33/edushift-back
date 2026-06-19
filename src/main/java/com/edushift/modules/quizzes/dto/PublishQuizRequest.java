package com.edushift.modules.quizzes.dto;

/**
 * Optional JSON body for {@code POST /quizzes/{uuid}/publish}
 * (Sprint 7b / BE-7b.1). The body is currently empty (publish is
 * a pure lifecycle action) but the record is reserved so the FE can
 * add an {@code acknowledge} flag in a later release without
 * changing the URL or HTTP verb.
 */
public record PublishQuizRequest() {
}
