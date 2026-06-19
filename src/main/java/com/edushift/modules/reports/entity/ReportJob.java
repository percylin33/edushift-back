package com.edushift.modules.reports.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Report generation job (Sprint 9 / BE-9.2).
 *
 * <p>One row per requested report. The {@code ReportJobProcessor}
 * picks PENDING rows, runs the appropriate generator, uploads the
 * output to {@code file_objects}, and updates the job status.</p>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   PENDING → RUNNING → DONE   (success; output_file_id set)
 *   PENDING → RUNNING → FAILED (gen error; error_code/message set)
 *   PENDING → CANCELLED        (user cancel before pickup)
 *   RUNNING  → FAILED          (sweeper timeout)
 * </pre>
 *
 * <h3>Idempotency</h3>
 * If the FE supplies a non-empty {@code idemKey}, a second POST with
 * the same key (same user, same tenant) returns the original job
 * instead of creating a new one. Prevents the "double-click → two
 * identical grade book PDFs" problem.
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}; queries auto-scoped.
 */
@Entity
@Table(name = "report_jobs", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class ReportJob extends TenantAwareEntity {

    public enum Format { PDF, XLSX, CSV }

    public enum Status { PENDING, RUNNING, DONE, FAILED, CANCELLED }

    /** What to generate. */
    public enum ReportType {
        GRADE_BOOK,         // Libreta de calificaciones por curso/sección
        ATTENDANCE_SUMMARY, // Resumen de asistencia por sección/periodo
        PERIOD_CLOSE,       // Cierre de periodo (snapshot firmado)
        STUDENT_TRANSCRIPT  // Historial académico del estudiante
    }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private Format format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params = "{}";

    @Column(name = "idem_key", nullable = false, length = 80)
    private String idemKey = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct = 0;

    @Column(name = "output_file_id")
    private UUID outputFileId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plusSeconds(600); // 10 min

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
