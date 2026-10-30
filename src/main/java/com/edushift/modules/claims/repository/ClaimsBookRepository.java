package com.edushift.modules.claims.repository;

import com.edushift.modules.claims.entity.ClaimsBookEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimsBookRepository extends JpaRepository<ClaimsBookEntry, UUID> {

	Optional<ClaimsBookEntry> findByClaimCode(String claimCode);

	Page<ClaimsBookEntry> findByStatus(ClaimsBookEntry.ClaimStatus status, Pageable pageable);

	@Query("SELECT COUNT(c) FROM ClaimsBookEntry c WHERE YEAR(c.createdAt) = :year")
	long countByYear(int year);
}
