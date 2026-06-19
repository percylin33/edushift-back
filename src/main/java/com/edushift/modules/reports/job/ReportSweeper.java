package com.edushift.modules.reports.job;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.repository.ReportJobRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Report sweeper (Sprint 9 / BE-9.2).
 *
 * <p>Marks {@code RUNNING} jobs past their {@code expires_at} as
 * {@code FAILED}. The processor can crash without freeing the
 * slot; this sweeper is the safety net. Runs every minute.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportSweeper {

    private final ReportJobRepository jobRepo;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void sweep() {
        List<ReportJob> zombies = jobRepo.findZombies(Instant.now());
        if (zombies.isEmpty()) return;
        for (ReportJob j : zombies) {
            j.setStatus(Status.FAILED);
            j.setErrorCode("TIMEOUT");
            j.setErrorMessage("Report generation exceeded timeout");
            j.setFinishedAt(Instant.now());
            jobRepo.save(j);
        }
        log.warn("[ReportSweeper] marked {} zombie jobs as FAILED", zombies.size());
    }
}
