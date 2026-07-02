package com.edushift.modules.evaluations.graderecord.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse.EvaluationRef;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse.StudentRef;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeRecordDtoTest {

    @Test
    @DisplayName("CreateGradeRecordRequest — accessors")
    void createRequest() {
        var req = new CreateGradeRecordRequest(
                "11111111-2222-3333-4444-555555555555",
                new BigDecimal("14.00"),
                null,
                "Great work");
        assertThat(req.studentPublicUuid())
                .isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(req.score()).isEqualByComparingTo("14.00");
        assertThat(req.literal()).isNull();
        assertThat(req.comments()).isEqualTo("Great work");
    }

    @Test
    @DisplayName("UpdateGradeRecordRequest — isEmpty")
    void updateIsEmpty() {
        assertThat(new UpdateGradeRecordRequest(null, null, null).isEmpty()).isTrue();
        var nonEmpty = new UpdateGradeRecordRequest(new BigDecimal("12.50"), null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.score()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("GradeRecordFilters — accessors")
    void filters() {
        UUID student = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        var f = new GradeRecordFilters(student, section, Boolean.TRUE);
        assertThat(f.studentPublicUuid()).isEqualTo(student);
        assertThat(f.sectionPublicUuid()).isEqualTo(section);
        assertThat(f.isActive()).isTrue();
    }

    @Test
    @DisplayName("GradeRecordListItem — accessors")
    void listItem() {
        UUID puuid = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        Instant t = Instant.now();
        var item = new GradeRecordListItem(
                puuid, student, "Jane", "Doe", "Smith",
                new BigDecimal("15.00"), null, "Good",
                t, Boolean.TRUE,
                t, t);
        assertThat(item.publicUuid()).isEqualTo(puuid);
        assertThat(item.studentPublicUuid()).isEqualTo(student);
        assertThat(item.studentFirstName()).isEqualTo("Jane");
        assertThat(item.studentLastName()).isEqualTo("Doe");
        assertThat(item.studentSecondLastName()).isEqualTo("Smith");
        assertThat(item.score()).isEqualByComparingTo("15.00");
        assertThat(item.comments()).isEqualTo("Good");
        assertThat(item.recordedAt()).isEqualTo(t);
        assertThat(item.isActive()).isTrue();
    }

    @Test
    @DisplayName("GradeRecordResponse — accessors")
    void response() {
        UUID puuid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID evaluationUuid = UUID.randomUUID();
        UUID studentUuid = UUID.randomUUID();
        var ref = new EvaluationRef(evaluationUuid, "Midterm",
                EvaluationScale.SCORE_0_20, EvaluationStatus.PUBLISHED);
        var stu = new StudentRef(studentUuid, "Jane", "Doe", "Smith");
        Instant t = Instant.now();
        var resp = new GradeRecordResponse(
                puuid, ref, stu,
                new BigDecimal("15.00"), null, "Good",
                t, userId, Boolean.TRUE,
                t, t);
        assertThat(resp.publicUuid()).isEqualTo(puuid);
        assertThat(resp.evaluation()).isEqualTo(ref);
        assertThat(resp.student()).isEqualTo(stu);
        assertThat(resp.score()).isEqualByComparingTo("15.00");
        assertThat(resp.comments()).isEqualTo("Good");
        assertThat(resp.recordedByUserId()).isEqualTo(userId);
        assertThat(resp.isActive()).isTrue();
        assertThat(resp.recordedAt()).isEqualTo(t);
    }

    @Test
    @DisplayName("BulkGradeRecordRequest — accessors")
    void bulkRequest() {
        var rec = new CreateGradeRecordRequest(
                "11111111-2222-3333-4444-555555555555",
                new BigDecimal("15.00"), null, null);
        var req = new BulkGradeRecordRequest(List.of(rec));
        assertThat(req.records()).hasSize(1);
        assertThat(req.records().get(0)).isEqualTo(rec);
    }

    @Test
    @DisplayName("BulkGradeRecordResponse — accessors")
    void bulkResponse() {
        var rec = new GradeRecordResponse(
                UUID.randomUUID(), null, null, null, null, null,
                Instant.now(), UUID.randomUUID(), null, Instant.now(), Instant.now());
        var resp = new BulkGradeRecordResponse(5, 3, 2, List.of(rec));
        assertThat(resp.requested()).isEqualTo(5);
        assertThat(resp.created()).isEqualTo(3);
        assertThat(resp.updated()).isEqualTo(2);
        assertThat(resp.records()).hasSize(1);
    }
}