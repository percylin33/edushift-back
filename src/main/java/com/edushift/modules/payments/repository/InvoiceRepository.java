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

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = com.edushift.modules.payments.entity.Invoice$Status.PENDING
              AND i.dueAt < :now
            """)
    List<Invoice> findOverduePending(@Param("now") Instant now, Pageable pageable);
}
