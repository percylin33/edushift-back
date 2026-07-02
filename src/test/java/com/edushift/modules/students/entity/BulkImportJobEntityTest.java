package com.edushift.modules.students.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkImportJobEntityTest {

    @Test
    @DisplayName("defaults: status=PENDING, processedRows=0, errorRows=0")
    void defaults() {
        var j = new BulkImportJob();
        assertThat(j.getStatus()).isEqualTo(BulkImportStatus.PENDING);
        assertThat(j.getProcessedRows()).isZero();
        assertThat(j.getErrorRows()).isZero();
        assertThat(j.getErrors()).isNotNull().isEmpty();
        assertThat(j.getTotalRows()).isNull();
    }

    @Test
    @DisplayName("markStarted → PROCESSING + stamps startedAt")
    void markStarted() {
        var j = new BulkImportJob();
        j.markStarted();
        assertThat(j.getStatus()).isEqualTo(BulkImportStatus.PROCESSING);
        assertThat(j.getStartedAt()).isNotNull();
        assertThat(j.getStartedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("markCompleted → COMPLETED + stamps finishedAt")
    void markCompleted() {
        var j = new BulkImportJob();
        j.markStarted();
        j.markCompleted();
        assertThat(j.getStatus()).isEqualTo(BulkImportStatus.COMPLETED);
        assertThat(j.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed truncates long reasons to 500 chars")
    void markFailed() {
        var j = new BulkImportJob();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) sb.append('x');
        j.markFailed(sb.toString());
        assertThat(j.getStatus()).isEqualTo(BulkImportStatus.FAILED);
        assertThat(j.getFailReason()).hasSize(500);
        assertThat(j.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed with null reason is permitted")
    void markFailedNull() {
        var j = new BulkImportJob();
        j.markFailed(null);
        assertThat(j.getFailReason()).isNull();
        assertThat(j.getStatus()).isEqualTo(BulkImportStatus.FAILED);
    }

    @Test
    @DisplayName("incrementProcessed bumps the counter by 1")
    void incrementProcessed() {
        var j = new BulkImportJob();
        j.incrementProcessed();
        j.incrementProcessed();
        j.incrementProcessed();
        assertThat(j.getProcessedRows()).isEqualTo(3);
        assertThat(j.getErrorRows()).isZero();
    }

    @Test
    @DisplayName("recordRowError appends an error and bumps errorRows")
    void recordRowError() {
        var j = new BulkImportJob();
        j.recordRowError(2, "ROW_INVALID", "document number is blank");
        j.recordRowError(3, "ROW_DUPLICATE", "already exists");
        assertThat(j.getErrorRows()).isEqualTo(2);
        assertThat(j.getErrorsView()).hasSize(2);
        assertThat(j.getErrorsView().get(0).row()).isEqualTo(2);
        assertThat(j.getErrorsView().get(0).code()).isEqualTo("ROW_INVALID");
    }

    @Test
    @DisplayName("BulkImportStatus.isTerminal COMPLETED/FAILED")
    void terminalStates() {
        assertThat(BulkImportStatus.PENDING.isTerminal()).isFalse();
        assertThat(BulkImportStatus.PROCESSING.isTerminal()).isFalse();
        assertThat(BulkImportStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(BulkImportStatus.FAILED.isTerminal()).isTrue();
    }
}