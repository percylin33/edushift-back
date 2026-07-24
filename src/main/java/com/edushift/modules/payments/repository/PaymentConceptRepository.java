package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.PaymentConcept;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentConceptRepository extends JpaRepository<PaymentConcept, UUID> {

	Optional<PaymentConcept> findByPublicUuid(UUID publicUuid);

	Optional<PaymentConcept> findByPublicUuidAndDeletedFalse(UUID publicUuid);

	Optional<PaymentConcept> findByCodeAndDeletedFalse(String code);

	List<PaymentConcept> findByActiveTrueOrderBySortOrderAscNameAsc();

	Page<PaymentConcept> findByDeletedFalseOrderBySortOrderAscNameAsc(Pageable pageable);
}