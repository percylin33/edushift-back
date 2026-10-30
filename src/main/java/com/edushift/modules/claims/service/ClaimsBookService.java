package com.edushift.modules.claims.service;

import com.edushift.modules.claims.dto.ClaimResponse;
import com.edushift.modules.claims.dto.CreateClaimRequest;
import com.edushift.modules.claims.dto.RespondClaimRequest;
import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClaimsBookService {

	ClaimResponse createPublicClaim(CreateClaimRequest request);

	Page<ClaimResponse> listClaims(ClaimStatus status, Pageable pageable);

	ClaimResponse respondClaim(UUID id, RespondClaimRequest request);
}
