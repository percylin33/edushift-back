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
