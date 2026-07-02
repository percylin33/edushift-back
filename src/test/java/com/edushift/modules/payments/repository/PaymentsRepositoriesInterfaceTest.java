package com.edushift.modules.payments.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.payments.entity.Payment;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class PaymentsRepositoriesInterfaceTest {

    @Test
    @DisplayName("InvoiceRepository: findByPublicUuid / findByInternalId / findByIdempotencyKey / list / findOverduePending")
    void invoiceRepo() throws Exception {
        Class<?> repo = InvoiceRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByInternalId", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByIdempotencyKey", String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByGuardianUserIdOrderByIssuedAtDesc",
                UUID.class, Pageable.class).getReturnType()).isAssignableFrom(Page.class);
        assertThat(repo.getMethod("findByStudentIdOrderByIssuedAtDesc",
                UUID.class, Pageable.class).getReturnType()).isAssignableFrom(Page.class);
        assertThat(repo.getMethod("findOverduePending", Instant.class, Pageable.class)
                .getReturnType()).isAssignableFrom(List.class);
    }

    @Test
    @DisplayName("PaymentRepository: findByPublicUuid / findByProviderAndExternalId / findByInvoiceIdOrderByCreatedAtDesc / adminList")
    void paymentRepo() throws Exception {
        Class<?> repo = PaymentRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByProviderAndExternalId",
                Payment.Provider.class, String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByInvoiceIdOrderByCreatedAtDesc", UUID.class)
                .getReturnType()).isAssignableFrom(List.class);
        assertThat(repo.getMethod("adminList",
                UUID.class, Payment.Status.class, Payment.Provider.class,
                String.class, Pageable.class).getReturnType())
                .isAssignableFrom(Page.class);
    }

    @Test
    @DisplayName("InvoiceItemRepository: findByInvoiceIdOrderByCreatedAtAsc returns List")
    void invoiceItemRepo() throws Exception {
        Class<?> repo = InvoiceItemRepository.class;
        assertThat(repo.getMethod("findByInvoiceIdOrderByCreatedAtAsc", UUID.class)
                .getReturnType()).isAssignableFrom(List.class);
    }

    @Test
    @DisplayName("SubscriptionRepository: findByPublicUuid / list / findDueForBilling")
    void subscriptionRepo() throws Exception {
        Class<?> repo = SubscriptionRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByGuardianUserIdOrderByCreatedAtDesc",
                UUID.class, Pageable.class).getReturnType()).isAssignableFrom(Page.class);
        assertThat(repo.getMethod("findDueForBilling", Instant.class, Pageable.class)
                .getReturnType()).isAssignableFrom(List.class);
    }
}