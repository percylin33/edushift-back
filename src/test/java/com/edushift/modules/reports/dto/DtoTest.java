package com.edushift.modules.reports.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reports DTOs")
class DtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    // -------- CreateReportRequest -------------------------------------------

    @Test
    @DisplayName("CreateReportRequest — record accessors")
    void createReportRequestAccessors() {
        var req = new CreateReportRequest(
            ReportType.GRADE_BOOK, Format.PDF, "{\"foo\":1}", "key-1");

        assertThat(req.reportType()).isEqualTo(ReportType.GRADE_BOOK);
        assertThat(req.format()).isEqualTo(Format.PDF);
        assertThat(req.params()).isEqualTo("{\"foo\":1}");
        assertThat(req.idemKey()).isEqualTo("key-1");
    }

    @Test
    @DisplayName("CreateReportRequest — valid (no violations)")
    void createReportRequestValid() {
        var req = new CreateReportRequest(
            ReportType.ATTENDANCE_SUMMARY, Format.CSV, null, null);

        var violations = validator.validate(req);

        assertThat(violations).isEmpty();
        assertThat(req.params()).isNull();
        assertThat(req.idemKey()).isNull();
    }

    @Test
    @DisplayName("CreateReportRequest — null reportType and format → 2 violations")
    void createReportRequestMissingEnums() {
        var req = new CreateReportRequest(null, null, "", "");

        var violations = validator.validate(req);

        assertThat(violations).hasSize(2);
        assertThat(violations.stream()
            .map(v -> v.getPropertyPath().toString()))
            .containsExactlyInAnyOrder("reportType", "format");
    }

    @Test
    @DisplayName("CreateReportRequest — params over 8000 chars → 1 violation")
    void createReportRequestParamsTooLong() {
        var huge = "x".repeat(8001);
        var req = new CreateReportRequest(ReportType.GRADE_BOOK, Format.PDF, huge, null);

        var violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
            .isEqualTo("params");
    }

    @Test
    @DisplayName("CreateReportRequest — idemKey over 80 chars → 1 violation")
    void createReportRequestIdemKeyTooLong() {
        var huge = "k".repeat(81);
        var req = new CreateReportRequest(ReportType.GRADE_BOOK, Format.PDF, null, huge);

        var violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
            .isEqualTo("idemKey");
    }

    // -------- ReportJobResponse ---------------------------------------------

    @Test
    @DisplayName("ReportJobResponse — record accessors")
    void reportJobResponseAccessors() {
        var pub = UUID.randomUUID();
        var resp = new ReportJobResponse(
            pub, ReportType.PERIOD_CLOSE, Format.XLSX, Status.DONE,
            (short) 100, null, null,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-06-01T00:01:00Z"),
            Instant.parse("2026-06-01T00:02:00Z"));

        assertThat(resp.publicUuid()).isEqualTo(pub);
        assertThat(resp.reportType()).isEqualTo(ReportType.PERIOD_CLOSE);
        assertThat(resp.format()).isEqualTo(Format.XLSX);
        assertThat(resp.status()).isEqualTo(Status.DONE);
        assertThat(resp.progressPct()).isEqualTo((short) 100);
        assertThat(resp.errorCode()).isNull();
        assertThat(resp.errorMessage()).isNull();
        assertThat(resp.requestedAt()).isNotNull();
        assertThat(resp.startedAt()).isNotNull();
        assertThat(resp.finishedAt()).isNotNull();
    }

    @Test
    @DisplayName("ReportJobResponse.from — maps every entity field")
    void reportJobResponseFrom() {
        var pub = UUID.randomUUID();
        var j = new ReportJob();
        j.setPublicUuid(pub);
        j.setReportType(ReportType.STUDENT_TRANSCRIPT);
        j.setFormat(Format.PDF);
        j.setStatus(Status.FAILED);
        j.setProgressPct((short) 60);
        j.setErrorCode("ERR");
        j.setErrorMessage("oops");
        var req = Instant.parse("2026-06-01T00:00:00Z");
        var start = Instant.parse("2026-06-01T00:01:00Z");
        var fin = Instant.parse("2026-06-01T00:02:00Z");
        j.setRequestedAt(req);
        j.setStartedAt(start);
        j.setFinishedAt(fin);

        var resp = ReportJobResponse.from(j);

        assertThat(resp.publicUuid()).isEqualTo(pub);
        assertThat(resp.reportType()).isEqualTo(ReportType.STUDENT_TRANSCRIPT);
        assertThat(resp.format()).isEqualTo(Format.PDF);
        assertThat(resp.status()).isEqualTo(Status.FAILED);
        assertThat(resp.progressPct()).isEqualTo((short) 60);
        assertThat(resp.errorCode()).isEqualTo("ERR");
        assertThat(resp.errorMessage()).isEqualTo("oops");
        assertThat(resp.requestedAt()).isEqualTo(req);
        assertThat(resp.startedAt()).isEqualTo(start);
        assertThat(resp.finishedAt()).isEqualTo(fin);
    }
}
