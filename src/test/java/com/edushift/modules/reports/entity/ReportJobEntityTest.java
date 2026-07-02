package com.edushift.modules.reports.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportJobEntityTest {

    private ReportJob job;

    @BeforeEach
    void setUp() {
        job = new ReportJob();
    }

    @Test
    @DisplayName("default state — status=PENDING, params='{}', idemKey='', progress=0")
    void defaults() {
        assertThat(job.getStatus()).isEqualTo(Status.PENDING);
        assertThat(job.getParams()).isEqualTo("{}");
        assertThat(job.getIdemKey()).isEmpty();
        assertThat(job.getProgressPct()).isZero();
        assertThat(job.getRequestedAt()).isNotNull();
        assertThat(job.getExpiresAt()).isAfter(job.getRequestedAt());
    }

    @Test
    @DisplayName("setters + getters round-trip")
    void settersAndGetters() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        job.setTenantId(tenantId);
        job.setRequestedByUserId(userId);
        job.setReportType(ReportType.STUDENT_TRANSCRIPT);
        job.setFormat(Format.PDF);
        job.setParams("{\"studentId\":\"x\"}");
        job.setIdemKey("abc");
        job.setStatus(Status.RUNNING);
        job.setProgressPct((short) 50);
        job.setOutputFileId(fileId);
        job.setErrorCode("BOOM");
        job.setErrorMessage("something");
        job.setRequestedAt(now);
        job.setStartedAt(now);
        job.setFinishedAt(now);

        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getRequestedByUserId()).isEqualTo(userId);
        assertThat(job.getReportType()).isEqualTo(ReportType.STUDENT_TRANSCRIPT);
        assertThat(job.getFormat()).isEqualTo(Format.PDF);
        assertThat(job.getParams()).isEqualTo("{\"studentId\":\"x\"}");
        assertThat(job.getIdemKey()).isEqualTo("abc");
        assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(job.getProgressPct()).isEqualTo((short) 50);
        assertThat(job.getOutputFileId()).isEqualTo(fileId);
        assertThat(job.getErrorCode()).isEqualTo("BOOM");
        assertThat(job.getErrorMessage()).isEqualTo("something");
        assertThat(job.getRequestedAt()).isEqualTo(now);
        assertThat(job.getStartedAt()).isEqualTo(now);
        assertThat(job.getFinishedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("enum counts — Format=3, Status=5, ReportType=4")
    void enumCounts() {
        assertThat(Format.values()).hasSize(3);
        assertThat(Status.values()).hasSize(5);
        assertThat(ReportType.values()).hasSize(4);
    }

    @Test
    @DisplayName("enum values usable via valueOf")
    void enumValueOf() {
        assertThat(Format.valueOf("CSV")).isEqualTo(Format.CSV);
        assertThat(Status.valueOf("DONE")).isEqualTo(Status.DONE);
        assertThat(ReportType.valueOf("PERIOD_CLOSE")).isEqualTo(ReportType.PERIOD_CLOSE);
    }

    @Test
    @DisplayName("@PrePersist onCreate — publicUuid assigned when null")
    void prePersistAssignsPublicUuid() throws Exception {
        assertThat(job.getPublicUuid()).isNull();

        var m = ReportJob.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(job);

        assertThat(job.getPublicUuid()).isNotNull();
        // Re-invoking must NOT overwrite a previously set UUID.
        var preserved = job.getPublicUuid();
        m.invoke(job);
        assertThat(job.getPublicUuid()).isEqualTo(preserved);
    }
}
