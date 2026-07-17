package com.edushift.modules.admin.invoicing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface B2BPaymentRepository extends JpaRepository<B2BPayment, UUID> {

    List<B2BPayment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Page<B2BPayment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByExternalRef(String externalRef);
}
