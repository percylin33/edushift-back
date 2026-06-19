package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPublicUuid(UUID publicUuid);

    /**
     * Webhook replay safety: lookup by MercadoPago payment id (the
     * unique key MP sends us). Returns empty on first webhook.
     */
    Optional<Payment> findByProviderAndExternalId(
            com.edushift.modules.payments.entity.Payment.Provider provider, String externalId);

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
}
