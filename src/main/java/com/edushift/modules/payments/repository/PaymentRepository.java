package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPublicUuid(UUID publicUuid);

    /**
     * Webhook replay safety: lookup by MercadoPago payment id (the
     * unique key MP sends us). Returns empty on first webhook.
     */
    Optional<Payment> findByProviderAndExternalId(
            com.edushift.modules.payments.entity.Payment.Provider provider, String externalId);

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    // -----------------------------------------------------------------------
    // Admin listing (Sprint 12 / DEBT-11-PAY-1).
    //
    // The query is hand-written (not Specification-based) because:
    //   1. The filter surface is small (3 fields) and stable.
    //   2. The {@code tenant_id} predicate is mandatory and must NEVER be
    //      omitted — the query uses an explicit {@code :tenantId} parameter
    //      so a future developer who adds a new filter cannot accidentally
    //      bypass tenant isolation by forgetting to wire the spec.
    //   3. We want the SQL to be predictable so the IT can assert
    //      cross-tenant safety by counting rows per tenant.
    //
    // The CASE-WHEN chain for {@code :status} / {@code :provider} lets the
    // caller pass NULL for "no filter" without resorting to OR-chains or
    // dynamic SQL. Postgres optimizes the trivial CASE branches away.
    // -----------------------------------------------------------------------

    /**
     * Tenant-scoped payment listing for the admin UI.
     *
     * <p>Always scoped by {@code :tenantId}; the caller is responsible for
     * passing the tenant id from the security context (the
     * {@link com.edushift.infrastructure.multitenancy.TenantContext}).</p>
     *
     * <h3>Why {@code COALESCE} + the explicit empty string</h3>
     * <p>Hibernate passes the {@code String :search} parameter as
     * {@code OTHER} (which the Postgres driver maps to {@code bytea})
     * when the Java type is {@code String} but the column is
     * {@code varchar}. That breaks the implicit
     * {@code lower(LOWER(...)) LIKE lower(...)} with the error
     * {@code function lower(bytea) does not exist}. Wrapping the
     * parameter in {@code COALESCE(..., '')} forces Hibernate to keep
     * the value as a string on the way down.</p>
     *
     * @param tenantId  the caller's tenant (UUID, NOT NULL)
     * @param status    optional status filter; NULL means "any"
     * @param provider  optional provider filter; NULL means "any"
     * @param search    optional ILIKE term (matches {@code external_id} or
     *                  {@code external_reference}); NULL means "no search"
     * @param pageable  Spring Data {@link Pageable} (page, size, sort)
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
              AND (:provider IS NULL OR p.provider = :provider)
              AND (COALESCE(:search, '') = '' OR
                   LOWER(p.externalId) LIKE LOWER(CONCAT('%', COALESCE(:search, ''), '%')) OR
                   LOWER(p.externalReference) LIKE LOWER(CONCAT('%', COALESCE(:search, ''), '%')))
            """)
    Page<Payment> adminList(
            @Param("tenantId") UUID tenantId,
            @Param("status") Payment.Status status,
            @Param("provider") Payment.Provider provider,
            @Param("search") String search,
            Pageable pageable);
}
