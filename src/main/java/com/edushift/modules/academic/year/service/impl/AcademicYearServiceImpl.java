package com.edushift.modules.academic.year.service.impl;

import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.mapper.AcademicYearMapper;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.academic.year.service.AcademicYearService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AcademicYearService}.
 *
 * <p>All write operations are wrapped in {@code @Transactional}; the
 * tenant scope comes from {@code TenantContext} (set by the request
 * filter). Queries inherit Hibernate's {@code @TenantId} discriminator,
 * so no explicit filtering is needed here.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicYearServiceImpl implements AcademicYearService {

	private final AcademicYearRepository repository;
	private final AcademicYearMapper mapper;

	@Override
	@Transactional(readOnly = true)
	public List<AcademicYearListItem> listYears(AcademicYearStatus statusFilter) {
		List<AcademicYear> years = (statusFilter == null)
				? repository.findAllByOrderByStartDateDesc()
				: repository.findAllByStatusOrderByStartDateDesc(statusFilter);

		return years.stream()
				// active first, then by startDate desc
				.sorted(Comparator
						.comparing((AcademicYear y) -> y.getStatus() != AcademicYearStatus.ACTIVE)
						.thenComparing(AcademicYear::getStartDate, Comparator.reverseOrder()))
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AcademicYearResponse getYear(UUID publicUuid) {
		return mapper.toResponse(loadYear(publicUuid));
	}

	@Override
	@Transactional
	public AcademicYearResponse createYear(CreateAcademicYearRequest request) {
		validateDateRange(request.startDate(), request.endDate());
		ensureNameAvailable(request.name(), null);

		AcademicYear year = mapper.fromCreate(request);

		try {
			AcademicYear saved = repository.saveAndFlush(year);
			log.info("[academic.year] created -- publicUuid={} name={}",
					saved.getPublicUuid(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Race condition: another tx inserted the same name (case-insensitive)
			// between our pre-check and the flush. The unique partial index
			// fired. Translate to the proper domain code.
			throw new ConflictException("ACADEMIC_YEAR_NAME_TAKEN",
					"Another academic year in this tenant already uses the name '"
							+ request.name() + "'", ex);
		}
	}

	@Override
	@Transactional
	public AcademicYearResponse updateYear(UUID publicUuid, UpdateAcademicYearRequest request) {
		AcademicYear year = loadYear(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(year);
		}

		if (year.getStatus() == AcademicYearStatus.CLOSED) {
			throw new ConflictException("ACADEMIC_YEAR_LOCKED",
					"Closed academic years are read-only");
		}

		LocalDate newStart = request.startDate() != null ? request.startDate() : year.getStartDate();
		LocalDate newEnd = request.endDate() != null ? request.endDate() : year.getEndDate();
		validateDateRange(newStart, newEnd);

		if (request.name() != null && !request.name().trim().equalsIgnoreCase(year.getName())) {
			ensureNameAvailable(request.name(), year.getId());
		}

		mapper.applyUpdate(request, year);

		try {
			AcademicYear saved = repository.saveAndFlush(year);
			log.info("[academic.year] updated -- publicUuid={} name={}",
					saved.getPublicUuid(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("ACADEMIC_YEAR_NAME_TAKEN",
					"Another academic year in this tenant already uses the name '"
							+ request.name() + "'", ex);
		}
	}

	@Override
	@Transactional
	public AcademicYearResponse activateYear(UUID publicUuid) {
		AcademicYear target = loadYear(publicUuid);

		if (target.getStatus() == AcademicYearStatus.ACTIVE) {
			log.debug("[academic.year] activate -- {} already ACTIVE; no-op", target.getPublicUuid());
			return mapper.toResponse(target);
		}

		if (!target.getStatus().isActivatable()) {
			log.warn("[academic.year] activate refused -- publicUuid={} status={}",
					target.getPublicUuid(), target.getStatus());
			throw new ConflictException("ACADEMIC_YEAR_NOT_ACTIVATABLE",
					"Academic year cannot be activated from status " + target.getStatus());
		}

		// Close the currently ACTIVE one (if any) before activating the target.
		// Atomic in the same tx + the unique partial index is the last line
		// of defense against concurrent activates.
		repository.findFirstByStatus(AcademicYearStatus.ACTIVE)
				.ifPresent(current -> {
					if (!current.getId().equals(target.getId())) {
						current.setStatus(AcademicYearStatus.CLOSED);
						repository.save(current);
						log.info("[academic.year] activate -- closed previous ACTIVE publicUuid={}",
								current.getPublicUuid());
					}
				});

		target.setStatus(AcademicYearStatus.ACTIVE);

		try {
			AcademicYear saved = repository.saveAndFlush(target);
			log.info("[academic.year] activated -- publicUuid={} name={}",
					saved.getPublicUuid(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Concurrent activate beat us (uk_academic_years_tenant_active fired).
			throw new ConflictException("ACADEMIC_YEAR_ALREADY_ACTIVE",
					"Another academic year was activated concurrently. Refresh and retry.", ex);
		}
	}

	@Override
	@Transactional
	public void deleteYear(UUID publicUuid) {
		AcademicYear year = loadYear(publicUuid);

		if (year.getStatus() == AcademicYearStatus.ACTIVE) {
			throw new ConflictException("ACADEMIC_YEAR_IN_USE",
					"Active academic years cannot be deleted; close it first");
		}
		// Future BE-4.3+ will add: reject if there are sections, periods, etc.

		repository.delete(year);
		log.info("[academic.year] deleted -- publicUuid={} name={}",
				year.getPublicUuid(), year.getName());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private AcademicYear loadYear(UUID publicUuid) {
		return repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private void ensureNameAvailable(String name, UUID excludeInternalId) {
		if (name == null) {
			return;
		}
		String trimmed = name.trim();
		repository.findByNameIgnoreCase(trimmed)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("ACADEMIC_YEAR_NAME_TAKEN",
							"Another academic year in this tenant already uses the name '"
									+ trimmed + "'");
				});
	}

	private void validateDateRange(LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			throw new ConflictException("ACADEMIC_YEAR_INVALID_DATE_RANGE",
					"startDate and endDate are required");
		}
		if (!start.isBefore(end)) {
			throw new ConflictException("ACADEMIC_YEAR_INVALID_DATE_RANGE",
					"startDate must be strictly before endDate");
		}
	}
}
