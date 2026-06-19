package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.InvoiceItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
