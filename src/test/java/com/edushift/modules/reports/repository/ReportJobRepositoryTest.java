package com.edushift.modules.reports.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportJobRepositoryTest {

    @Mock private ReportJobRepository repo;

    private UUID tenantId;
    private UUID userId;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        pageable = PageRequest.of(0, 10);
    }

    private ReportJob stubJob(Status status) {
        var j = new ReportJob();
        j.setPublicUuid(UUID.randomUUID());
        j.setTenantId(tenantId);
        j.setRequestedByUserId(userId);
        j.setReportType(ReportType.GRADE_BOOK);
        j.setFormat(Format.PDF);
        j.setStatus(status);
        j.setRequestedAt(Instant.now());
        return j;
    }

    @Test
    @DisplayName("findByPublicUuid — delegate to JPA")
    void findByPublicUuid() {
        var job = stubJob(Status.PENDING);
        when(repo.findByPublicUuid(job.getPublicUuid())).thenReturn(Optional.of(job));

        var found = repo.findByPublicUuid(job.getPublicUuid());

        assertThat(found).isPresent().get().isSameAs(job);
        verify(repo).findByPublicUuid(job.getPublicUuid());
    }

    @Test
    @DisplayName("findByPublicUuid — empty when missing")
    void findByPublicUuidMissing() {
        when(repo.findByPublicUuid(any(UUID.class))).thenReturn(Optional.empty());

        assertThat(repo.findByPublicUuid(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByTenantIdAndUserId — returns scoped Page")
    void findByTenant() {
        var a = stubJob(Status.PENDING);
        var b = stubJob(Status.DONE);
        Page<ReportJob> page = new PageImpl<>(List.of(a, b), pageable, 2);
        when(repo.findByTenantIdAndUserId(eq(tenantId), eq(userId), eq(pageable)))
            .thenReturn(page);

        var result = repo.findByTenantIdAndUserId(tenantId, userId, pageable);

        assertThat(result.getContent()).hasSize(2).containsExactly(a, b);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(repo).findByTenantIdAndUserId(tenantId, userId, pageable);
    }

    @Test
    @DisplayName("findByIdemKey — present when key exists")
    void findByIdemKeyHit() {
        var job = stubJob(Status.DONE);
        when(repo.findByIdemKey(userId, "abc")).thenReturn(Optional.of(job));

        var result = repo.findByIdemKey(userId, "abc");

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByIdemKey — empty when key missing")
    void findByIdemKeyMiss() {
        when(repo.findByIdemKey(eq(userId), any())).thenReturn(Optional.empty());

        assertThat(repo.findByIdemKey(userId, "nope")).isEmpty();
    }

    @Test
    @DisplayName("findRecentByUser — returns ordered list slice")
    void findRecentByUser() {
        var a = stubJob(Status.DONE);
        var b = stubJob(Status.FAILED);
        when(repo.findRecentByUser(userId, pageable)).thenReturn(List.of(a, b));

        var result = repo.findRecentByUser(userId, pageable);

        assertThat(result).containsExactly(a, b);
    }

    @Test
    @DisplayName("pickPending — system call, returns up to N (SKIP LOCKED)")
    void pickPending() {
        var a = stubJob(Status.PENDING);
        when(repo.pickPending(5)).thenReturn(List.of(a));

        var result = repo.pickPending(5);

        assertThat(result).hasSize(1).contains(a);
    }

    @Test
    @DisplayName("pickPending — empty when no PENDING")
    void pickPendingEmpty() {
        when(repo.pickPending(5)).thenReturn(List.of());

        assertThat(repo.pickPending(5)).isEmpty();
    }

    @Test
    @DisplayName("findZombies — RUNNING jobs past expiry")
    void findZombies() {
        var z = stubJob(Status.RUNNING);
        when(repo.findZombies(any(Instant.class))).thenReturn(List.of(z));

        var result = repo.findZombies(Instant.now());

        assertThat(result).hasSize(1).contains(z);
    }
}
