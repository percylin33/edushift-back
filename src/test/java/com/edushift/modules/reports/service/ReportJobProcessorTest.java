package com.edushift.modules.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.job.ReportJobProcessor;
import com.edushift.modules.reports.job.ReportOutputCache;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportJobProcessorTest {

    @Mock private ReportJobRepository jobRepo;
    @Mock private ReportService reportService;

    private ReportJobProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReportJobProcessor(jobRepo, reportService);
        ReflectionTestUtils.setField(processor, "batchSize", 5);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReportJob stubJob(UUID id, UUID publicUuid) {
        var j = new ReportJob();
        j.setId(id);
        j.setPublicUuid(publicUuid);
        j.setTenantId(UUID.randomUUID());
        j.setReportType(ReportType.GRADE_BOOK);
        j.setFormat(Format.CSV);
        j.setStatus(Status.PENDING);
        j.setRequestedAt(Instant.now());
        j.setExpiresAt(Instant.now().plusSeconds(600));
        return j;
    }

    @Test
    @DisplayName("process — empty batch short-circuits")
    void emptyBatch() {
        when(jobRepo.pickPending(5)).thenReturn(List.of());

        processor.process();

        verify(jobRepo, times(1)).pickPending(5);
        verify(jobRepo, never()).save(any());
    }

    @Test
    @DisplayName("process — happy path: PENDING → RUNNING → DONE + cache populated")
    void happyPath() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.pickPending(5)).thenReturn(List.of(job));
        when(jobRepo.findById(id)).thenReturn(Optional.of(job));
        when(reportService.generateBytes(job)).thenReturn("csv".getBytes());

        processor.process();

        assertThat(job.getStatus()).isEqualTo(Status.DONE);
        assertThat(job.getProgressPct()).isEqualTo((short) 100);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getErrorCode()).isNull();
        assertThat(job.getErrorMessage()).isNull();
        verify(jobRepo, times(2)).save(job);
        var entry = ReportOutputCache.get(publicUuid);
        assertThat(entry).isNotNull();
        assertThat(entry.format()).isEqualTo(Format.CSV);
        ReportOutputCache.evict(publicUuid);
    }

    @Test
    @DisplayName("process — generator throws → status FAILED with GENERATION_FAILED")
    void generationFailure() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.pickPending(5)).thenReturn(List.of(job));
        when(jobRepo.findById(id)).thenReturn(Optional.of(job));
        when(reportService.generateBytes(job)).thenThrow(new IOException("boom"));

        processor.process();

        assertThat(job.getStatus()).isEqualTo(Status.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("GENERATION_FAILED");
        assertThat(job.getErrorMessage()).isEqualTo("boom");
        assertThat(job.getFinishedAt()).isNotNull();
        ReportOutputCache.evict(publicUuid);
    }

    @Test
    @DisplayName("process — exception message truncated to 2000 chars")
    void errorMessageTruncated() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.pickPending(5)).thenReturn(List.of(job));
        when(jobRepo.findById(id)).thenReturn(Optional.of(job));
        var huge = "x".repeat(5000);
        when(reportService.generateBytes(job)).thenThrow(new IOException(huge));

        processor.process();

        assertThat(job.getErrorMessage()).hasSize(2000);
        ReportOutputCache.evict(publicUuid);
    }

    @Test
    @DisplayName("process — findById returns empty: row gone, no save")
    void rowMissingDuringProcessing() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.pickPending(5)).thenReturn(List.of(job));
        when(jobRepo.findById(id)).thenReturn(Optional.empty());

        processor.process();

        verify(jobRepo, times(0)).save(any());
        verify(reportService, times(0)).generateBytes(any());
    }

    @Test
    @DisplayName("processOne — direct invocation succeeds")
    void processOneDirect() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.findById(id)).thenReturn(Optional.of(job));
        when(reportService.generateBytes(job)).thenReturn("ok".getBytes());

        processor.processOne(id);

        assertThat(job.getStatus()).isEqualTo(Status.DONE);
        ReportOutputCache.evict(publicUuid);
    }

    @Test
    @DisplayName("processOne — swallows runtime exceptions and persists FAILED")
    void processOneHandlesException() throws IOException {
        var id = UUID.randomUUID();
        var publicUuid = UUID.randomUUID();
        var job = stubJob(id, publicUuid);
        when(jobRepo.findById(id)).thenReturn(Optional.of(job));
        when(reportService.generateBytes(job)).thenThrow(new RuntimeException("explode"));

        processor.processOne(id);

        assertThat(job.getStatus()).isEqualTo(Status.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("GENERATION_FAILED");
        assertThat(job.getErrorMessage()).isEqualTo("explode");
        ReportOutputCache.evict(publicUuid);
    }

    @Test
    @DisplayName("process — multiple jobs in batch are all processed")
    void multipleJobsInBatch() throws IOException {
        var idA = UUID.randomUUID();
        var idB = UUID.randomUUID();
        var pubA = UUID.randomUUID();
        var pubB = UUID.randomUUID();
        var a = stubJob(idA, pubA);
        var b = stubJob(idB, pubB);
        when(jobRepo.pickPending(5)).thenReturn(List.of(a, b));
        when(jobRepo.findById(idA)).thenReturn(Optional.of(a));
        when(jobRepo.findById(idB)).thenReturn(Optional.of(b));
        when(reportService.generateBytes(any())).thenReturn("ok".getBytes());

        processor.process();

        assertThat(a.getStatus()).isEqualTo(Status.DONE);
        assertThat(b.getStatus()).isEqualTo(Status.DONE);
        ReportOutputCache.evict(pubA);
        ReportOutputCache.evict(pubB);
    }
}
