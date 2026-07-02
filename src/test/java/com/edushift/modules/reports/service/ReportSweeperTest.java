package com.edushift.modules.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.job.ReportSweeper;
import com.edushift.modules.reports.repository.ReportJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportSweeperTest {

    @Mock private ReportJobRepository jobRepo;
    @InjectMocks private ReportSweeper sweeper;

    private ReportJob zombie(ReportJob.Status status) {
        var j = new ReportJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(UUID.randomUUID());
        j.setStatus(status);
        j.setReportType(ReportJob.ReportType.GRADE_BOOK);
        j.setFormat(ReportJob.Format.CSV);
        j.setExpiresAt(Instant.now().minusSeconds(60));
        j.setRequestedAt(Instant.now().minusSeconds(120));
        return j;
    }

    @Test
    @DisplayName("sweep — empty list short-circuits")
    void nothingToSweep() {
        when(jobRepo.findZombies(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verify(jobRepo, times(1)).findZombies(any(Instant.class));
        verify(jobRepo, never()).save(any());
    }

    @Test
    @DisplayName("sweep — single zombie → FAILED + TIMEOUT + finishedAt stamped")
    void singleZombie() {
        var z = zombie(Status.RUNNING);
        when(jobRepo.findZombies(any(Instant.class))).thenReturn(List.of(z));

        sweeper.sweep();

        assertThat(z.getStatus()).isEqualTo(Status.FAILED);
        assertThat(z.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(z.getErrorMessage()).isEqualTo("Report generation exceeded timeout");
        assertThat(z.getFinishedAt()).isNotNull();
        verify(jobRepo, times(1)).save(z);
    }

    @Test
    @DisplayName("sweep — multiple zombies all transitioned to FAILED")
    void multipleZombies() {
        var a = zombie(Status.RUNNING);
        var b = zombie(Status.RUNNING);
        var c = zombie(Status.RUNNING);
        when(jobRepo.findZombies(any(Instant.class))).thenReturn(List.of(a, b, c));

        sweeper.sweep();

        assertThat(a.getStatus()).isEqualTo(Status.FAILED);
        assertThat(b.getStatus()).isEqualTo(Status.FAILED);
        assertThat(c.getStatus()).isEqualTo(Status.FAILED);
        verify(jobRepo, times(1)).save(a);
        verify(jobRepo, times(1)).save(b);
        verify(jobRepo, times(1)).save(c);
    }

    @Test
    @DisplayName("sweep — finishedAt is recent (within a few seconds of now)")
    void finishedAtFresh() {
        var z = zombie(Status.RUNNING);
        when(jobRepo.findZombies(any(Instant.class))).thenReturn(List.of(z));

        var before = Instant.now();
        sweeper.sweep();
        var after = Instant.now();

        assertThat(z.getFinishedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}
