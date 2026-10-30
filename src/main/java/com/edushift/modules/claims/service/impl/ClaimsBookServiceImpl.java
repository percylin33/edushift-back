package com.edushift.modules.claims.service.impl;

import com.edushift.modules.claims.dto.ClaimResponse;
import com.edushift.modules.claims.dto.CreateClaimRequest;
import com.edushift.modules.claims.dto.RespondClaimRequest;
import com.edushift.modules.claims.entity.ClaimsBookEntry;
import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimStatus;
import com.edushift.modules.claims.repository.ClaimsBookRepository;
import com.edushift.modules.claims.service.ClaimsBookService;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimsBookServiceImpl implements ClaimsBookService {

	private final ClaimsBookRepository repository;

	@Override
	@Transactional
	public ClaimResponse createPublicClaim(CreateClaimRequest request) {
		int currentYear = LocalDate.now().getYear();
		long countThisYear = repository.countByYear(currentYear) + 1;
		String claimCode = String.format("REC-%d-%04d", currentYear, countThisYear);

		LocalDate dueDate = calculate15BusinessDays(LocalDate.now());

		ClaimsBookEntry entry = new ClaimsBookEntry();
		// Claims are complaints against EduShift as the platform provider. The
		// public form must never choose a tenant or redirect a complaint to a school.
		entry.setTenantId(null);
		entry.setClaimCode(claimCode);
		entry.setConsumerName(request.consumerName());
		entry.setConsumerDocType(request.consumerDocType());
		entry.setConsumerDocNumber(request.consumerDocNumber());
		entry.setConsumerEmail(request.consumerEmail());
		entry.setConsumerPhone(request.consumerPhone());
		entry.setConsumerAddress(request.consumerAddress());
		entry.setIsMinor(Boolean.TRUE.equals(request.isMinor()));
		entry.setParentGuardianName(request.parentGuardianName());
		entry.setContractedService(request.contractedService());
		entry.setClaimAmount(request.claimAmount());
		entry.setClaimType(request.claimType());
		entry.setDetailDescription(request.detailDescription());
		entry.setConsumerRequest(request.consumerRequest());
		entry.setStatus(ClaimStatus.PENDING);
		entry.setDueDate(dueDate);

		ClaimsBookEntry saved = repository.save(entry);
		return mapToResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ClaimResponse> listClaims(ClaimStatus status, Pageable pageable) {
		if (status != null) {
			return repository.findByStatus(status, pageable).map(this::mapToResponse);
		}
		return repository.findAll(pageable).map(this::mapToResponse);
	}

	@Override
	@Transactional
	public ClaimResponse respondClaim(UUID id, RespondClaimRequest request) {
		ClaimsBookEntry entry = repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado con id: " + id));

		entry.setResponseText(request.responseText());
		entry.setRespondedAt(Instant.now());
		entry.setStatus(ClaimStatus.ATTENDED);

		ClaimsBookEntry updated = repository.save(entry);
		return mapToResponse(updated);
	}

	/**
	 * Calcula 15 días hábiles a partir de la fecha actual de registro (Ley N° 32495).
	 */
	private LocalDate calculate15BusinessDays(LocalDate startDate) {
		LocalDate date = startDate;
		int addedDays = 0;
		while (addedDays < 15) {
			date = date.plusDays(1);
			if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
				addedDays++;
			}
		}
		return date;
	}

	private ClaimResponse mapToResponse(ClaimsBookEntry entry) {
		return new ClaimResponse(
				entry.getId(),
				entry.getClaimCode(),
				entry.getConsumerName(),
				entry.getConsumerDocType(),
				entry.getConsumerDocNumber(),
				entry.getConsumerEmail(),
				entry.getConsumerPhone(),
				entry.getConsumerAddress(),
				entry.getIsMinor(),
				entry.getParentGuardianName(),
				entry.getContractedService(),
				entry.getClaimAmount(),
				entry.getClaimType(),
				entry.getDetailDescription(),
				entry.getConsumerRequest(),
				entry.getStatus(),
				entry.getDueDate(),
				entry.getResponseText(),
				entry.getRespondedAt(),
				entry.getCreatedAt()
		);
	}
}
