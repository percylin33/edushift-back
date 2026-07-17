package com.edushift.modules.qa.service;

import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.qa.QaBugReport;
import com.edushift.modules.qa.dto.QaBugReportResponse;
import com.edushift.modules.qa.repository.QaBugReportRepository;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.TooManyRequestsException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for QA bug reports created from the Centro de Pruebas ({@code /help}).
 *
 * <h3>Tenant isolation</h3>
 * Tenant id is always taken from the session via {@link CurrentUserProvider}.
 * For SUPER_ADMIN (no tenant scope), {@code tenantId} is stored as NULL.
 * Non-SA actors can only see their own reports.
 *
 * <h3>Rate limiting</h3>
 * {@code POST /bug-reports} is capped at 30 req/min/IP via {@link SimpleRateLimiter}.
 *
 * <h3>Append-only semantics</h3>
 * Reports are never hard-deleted; the only mutation API is {@link #updateStatus}
 * for OPEN → ACKNOWLEDGED → RESOLVED transitions.
 */
@Service
public class QaBugReportService {

    private static final int QA_BUG_REPORT_MAX_PER_MINUTE = 30;
    private static final long QA_BUG_REPORT_WINDOW_MS = 60_000L;

    private final QaBugReportRepository repository;
    private final CurrentUserProvider currentUser;
    private final UserRepository userRepository;
    private final SimpleRateLimiter rateLimiter;

    @Autowired
    public QaBugReportService(
            QaBugReportRepository repository,
            CurrentUserProvider currentUser,
            UserRepository userRepository,
            SimpleRateLimiter rateLimiter) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public QaBugReportResponse create(CreateCommand cmd) {
        enforceRateLimit();
        UUID actorId = currentUser.currentUserId()
                .map(this::resolveInternalUserId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_REQUIRED",
                        "Authenticated session is required to report bugs."));
        Optional<UUID> tenantOpt = currentUser.currentTenantId();

        QaBugReport report = new QaBugReport();
        report.setTenantId(tenantOpt.orElse(null));
        report.setActorId(actorId);
        report.setCapabilityId(cmd.capabilityId());
        report.setStepId(cmd.stepId());
        report.setStepLabel(cmd.stepLabel());
        report.setSeverity(cmd.severity());
        report.setStatus(QaBugReport.Status.OPEN);
        report.setNotes(cmd.notes());
        report.setRequest(cmd.request());
        return QaBugReportResponse.of(repository.save(report));
    }

    @Transactional(readOnly = true)
    public Page<QaBugReportResponse> search(
            String capabilityId,
            String status,
            UUID requesterInternalId,
            Pageable pageable) {

        Optional<UUID> tenantOpt = currentUser.currentTenantId();
        UUID tenantId = tenantOpt.orElse(null);
        return repository.search(tenantId, requesterInternalId, capabilityId, status, pageable)
                .map(QaBugReportResponse::of);
    }

    @Transactional
    public QaBugReportResponse updateStatus(UUID publicUuid, QaBugReport.Status newStatus) {
        QaBugReport report = repository.findById(publicUuid)
                .orElseThrow(() -> new ForbiddenException(
                        "BUG_REPORT_NOT_FOUND",
                        "No bug report with id " + publicUuid));
        UUID requester = currentUser.currentUserId()
                .map(this::resolveInternalUserId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_REQUIRED", "Authentication required."));
        boolean isSa = currentUser.currentTenantId().isEmpty();
        boolean isOwner = report.getActorId() != null && report.getActorId().equals(requester);
        if (!isSa && !isOwner) {
            throw new ForbiddenException(
                    "FORBIDDEN", "Only the report owner or SUPER_ADMIN can change status.");
        }
        report.setStatus(newStatus);
        return QaBugReportResponse.of(repository.save(report));
    }

    private void enforceRateLimit() {
        String ip = "unknown";
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            if (req != null && req.getRemoteAddr() != null) {
                ip = req.getRemoteAddr();
            }
        }
        String ipKey = "qa-bug-reports:" + ip;
        if (!rateLimiter.allow(ipKey, QA_BUG_REPORT_MAX_PER_MINUTE,
                (int) QA_BUG_REPORT_WINDOW_MS)) {
            throw new TooManyRequestsException("QA_RATE_LIMITED",
                    "Too many bug reports from this IP. Retry in 60 seconds.");
        }
    }

    private UUID resolveInternalUserId(UUID publicUuid) {
        return userRepository.findByPublicUuid(publicUuid)
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Authenticated user no longer exists."));
    }

    public record CreateCommand(
            String capabilityId,
            String stepId,
            String stepLabel,
            QaBugReport.Severity severity,
            String notes,
            Map<String, Object> request
    ) {}
}