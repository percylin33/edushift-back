package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.Invoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPublicUuid(UUID publicUuid);

    /**
     * Internal-id lookup. Used by the admin service when walking
     * from a {@code Payment} (which carries the invoice's internal
     * UUID) to the parent {@code Invoice} without exposing
     * public uuids across the service boundary (Sprint 11 /
     * BE-11.8 / DEBT-10-PAY-1).
     *
     * <p>Named {@code findByInternalId} to avoid clashing with
     * {@code CrudRepository#findById} (whose generic {@code ID}
     * parameter on this repo is {@code Long}, not {@code UUID}).
     * Implemented as a JPQL query because the entity field is
     * {@code id} (not {@code internalId}) and Spring Data's
     * derived-name parser would otherwise fail with
     * {@code PropertyReferenceException}.</p>
     */
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByInternalId(@Param("id") UUID id);

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Page<Invoice> findByGuardianUserIdOrderByIssuedAtDesc(UUID guardianUserId, Pageable pageable);

    Page<Invoice> findByStudentIdOrderByIssuedAtDesc(UUID studentId, Pageable pageable);

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.2): list invoices that are either
     * owned by the given guardian OR attached to the given student.
     * Lets a STUDENT see their own invoices directly (without going
     * through the parent/guardian linkage), while keeping the
     * PARENT's "Mis pagos" flow unchanged.
     *
     * <p>Returns the same shape as
     * {@link #findByGuardianUserIdOrderByIssuedAtDesc} so callers can
     * reuse the same mapper. Cross-tenant isolation comes from the
     * {@code @TenantId} Hibernate filter applied to {@code Invoice}.</p>
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.guardianUserId = :guardianUserId
               OR i.studentId = :studentId
            ORDER BY i.issuedAt DESC
            """)
    Page<Invoice> findByGuardianOrStudentOrderByIssuedAtDesc(
            @Param("guardianUserId") UUID guardianUserId,
            @Param("studentId") UUID studentId,
            Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.guardianUserId = :guardianUserId
               OR i.studentId IN :studentIds
            ORDER BY i.issuedAt DESC
            """)
    Page<Invoice> findByGuardianOrStudentsOrderByIssuedAtDesc(
            @Param("guardianUserId") UUID guardianUserId,
            @Param("studentIds") List<UUID> studentIds,
            Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = com.edushift.modules.payments.entity.Invoice$Status.PENDING
              AND i.dueAt < :now
            """)
    List<Invoice> findOverduePending(@Param("now") Instant now, Pageable pageable);
}
