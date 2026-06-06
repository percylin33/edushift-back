package com.edushift.modules.academic.period.service.impl;

import com.edushift.modules.academic.period.dto.AcademicPeriodListItem;
import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.dto.UpdateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.mapper.AcademicPeriodMapper;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.period.service.AcademicPeriodService;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AcademicPeriodService}.
 *
 * <h3>Validation pipeline (write paths)</h3>
 * <ol>
 *   <li>Resolve year, reject if {@link AcademicYearStatus#CLOSED} →
 *       {@code ACADEMIC_YEAR_LOCKED}.</li>
 *   <li>Validate {@code endDate >= startDate} →
 *       {@code PERIOD_DATE_INVERTED}.</li>
 *   <li>Validate range inside year →
 *       {@code PERIOD_OUT_OF_YEAR_RANGE}.</li>
 *   <li>Validate ordinal contiguity (create only) →
 *       {@code PERIOD_ORDINAL_GAP} / {@code PERIOD_ORDINAL_TAKEN}.</li>
 *   <li>Validate no date overlap inside {@code (year, type)} via
 *       Postgres' {@code daterange &&} →
 *       {@code PERIOD_DATE_OVERLAP}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

	private final AcademicPeriodRepository periodRepository;
	private final AcademicYearRepository yearRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final AcademicPeriodMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<AcademicPeriodListItem> listPeriods(UUID academicYearPublicUuid,
			PeriodType periodType) {
		AcademicYear year = resolveYearForList(academicYearPublicUuid).orElse(null);
		if (year == null) return List.of();

		List<AcademicPeriod> periods = (periodType == null)
				? periodRepository.findAllByYear(year)
				: periodRepository.findAllByYearAndType(year, periodType);

		return periods.stream().map(mapper::toListItem).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AcademicPeriodResponse getPeriod(UUID publicUuid) {
		return mapper.toResponse(loadPeriod(publicUuid));
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public AcademicPeriodResponse createPeriod(CreateAcademicPeriodRequest request) {
		AcademicYear year = loadYear(request.academicYearPublicUuid());
		ensureYearIsEditable(year);

		validateDateOrder(request.startDate(), request.endDate());
		validateRangeInsideYear(year, request.startDate(), request.endDate());
		validateOrdinalForCreate(year, request.periodType(), request.ordinal());
		validateNoOverlap(year, request.periodType(),
				request.startDate(), request.endDate(), null);

		AcademicPeriod period = new AcademicPeriod();
		period.setAcademicYear(year);
		period.setPeriodType(request.periodType());
		period.setOrdinal(request.ordinal());
		period.setStartDate(request.startDate());
		period.setEndDate(request.endDate());
		period.setName(resolveName(request.name(), request.periodType(), request.ordinal()));

		try {
			AcademicPeriod saved = periodRepository.saveAndFlush(period);
			log.info("[academic.period] created -- publicUuid={} year={} type={} ordinal={}",
					saved.getPublicUuid(), year.getName(),
					saved.getPeriodType(), saved.getOrdinal());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// uk_academic_periods_year_type_ordinal_active fired — concurrent
			// duplicate. Surface as ORDINAL_TAKEN for the caller.
			throw new ConflictException("PERIOD_ORDINAL_TAKEN",
					"Ordinal " + request.ordinal() + " is already taken in "
							+ year.getName() + " / " + request.periodType(), ex);
		}
	}

	@Override
	@Transactional
	public AcademicPeriodResponse updatePeriod(UUID publicUuid,
			UpdateAcademicPeriodRequest request) {
		AcademicPeriod period = loadPeriod(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(period);
		}
		ensureYearIsEditable(period.getAcademicYear());

		LocalDate newStart = request.startDate() != null ? request.startDate() : period.getStartDate();
		LocalDate newEnd = request.endDate() != null ? request.endDate() : period.getEndDate();

		validateDateOrder(newStart, newEnd);
		validateRangeInsideYear(period.getAcademicYear(), newStart, newEnd);
		validateNoOverlap(period.getAcademicYear(), period.getPeriodType(),
				newStart, newEnd, period.getId());

		if (request.startDate() != null) period.setStartDate(newStart);
		if (request.endDate() != null) period.setEndDate(newEnd);
		if (request.name() != null && !request.name().isBlank()) {
			period.setName(request.name().trim());
		}

		AcademicPeriod saved = periodRepository.saveAndFlush(period);
		log.info("[academic.period] updated -- publicUuid={}", saved.getPublicUuid());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public void deletePeriod(UUID publicUuid) {
		AcademicPeriod period = loadPeriod(publicUuid);
		ensureYearIsEditable(period.getAcademicYear());

		// BE-4.7 / DEBT-ACAD-4: reject delete when active assignments
		// reference this period. The FK is RESTRICT — surface a clean
		// 409 instead of a generic integrity violation. We probe before
		// the contiguity check so the API returns the more useful
		// "in use" message even for the top-most ordinal.
		if (teacherAssignmentRepository.existsActiveByPeriod(period)) {
			throw new ConflictException("PERIOD_IN_USE_BY_ASSIGNMENTS",
					"Period '" + period.getName()
							+ "' has active teacher assignments. "
							+ "Soft-end them first.");
		}

		// Contiguity: only the highest ordinal can be deleted; otherwise we'd
		// leave a gap in (year, type).
		Integer maxOrdinal = periodRepository.findMaxOrdinalByYearAndType(
				period.getAcademicYear(), period.getPeriodType());
		if (maxOrdinal != null && period.getOrdinal() < maxOrdinal) {
			throw new ConflictException("PERIOD_NOT_LAST_ORDINAL",
					"Cannot delete ordinal " + period.getOrdinal()
							+ " while a higher ordinal (" + maxOrdinal
							+ ") exists in the same year/type. Delete the last one first.");
		}

		periodRepository.delete(period);
		log.info("[academic.period] deleted -- publicUuid={} ordinal={}",
				period.getPublicUuid(), period.getOrdinal());
	}

	// =========================================================================
	// Validations
	// =========================================================================

	private void validateDateOrder(LocalDate start, LocalDate end) {
		if (start == null || end == null) return; // bean validation handles nulls
		if (end.isBefore(start)) {
			throw new BusinessException("PERIOD_DATE_INVERTED",
					"endDate must be >= startDate (got "
							+ start + " .. " + end + ")");
		}
	}

	private void validateRangeInsideYear(AcademicYear year, LocalDate start, LocalDate end) {
		if (start.isBefore(year.getStartDate()) || end.isAfter(year.getEndDate())) {
			throw new ConflictException("PERIOD_OUT_OF_YEAR_RANGE",
					"Period [" + start + " .. " + end + "] is outside "
							+ year.getName() + " [" + year.getStartDate()
							+ " .. " + year.getEndDate() + "]");
		}
	}

	private void validateOrdinalForCreate(AcademicYear year, PeriodType type, int ordinal) {
		Integer max = periodRepository.findMaxOrdinalByYearAndType(year, type);
		int currentMax = (max == null) ? 0 : max;

		if (ordinal <= currentMax) {
			throw new ConflictException("PERIOD_ORDINAL_TAKEN",
					"Ordinal " + ordinal + " is already taken in "
							+ year.getName() + " / " + type);
		}
		if (ordinal > currentMax + 1) {
			throw new BusinessException("PERIOD_ORDINAL_GAP",
					"Cannot create ordinal " + ordinal + " — next available is "
							+ (currentMax + 1) + " in " + year.getName() + " / " + type);
		}
	}

	private void validateNoOverlap(AcademicYear year, PeriodType type,
			LocalDate start, LocalDate end, UUID excludeId) {
		Optional<UUID> overlap = periodRepository.findOverlap(
				year.getId(), type.name(), start, end, excludeId);
		if (overlap.isPresent()) {
			throw new ConflictException("PERIOD_DATE_OVERLAP",
					"Date range [" + start + " .. " + end
							+ "] overlaps another " + type + " in " + year.getName());
		}
	}

	private void ensureYearIsEditable(AcademicYear year) {
		if (!year.getStatus().isEditable()) {
			throw new ConflictException("ACADEMIC_YEAR_LOCKED",
					"Academic year '" + year.getName()
							+ "' is CLOSED. Periods cannot be modified.");
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private AcademicPeriod loadPeriod(UUID publicUuid) {
		return periodRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", publicUuid));
	}

	private AcademicYear loadYear(UUID publicUuid) {
		return yearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private Optional<AcademicYear> resolveYearForList(UUID publicUuid) {
		if (publicUuid != null) {
			return yearRepository.findByPublicUuid(publicUuid);
		}
		return yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE);
	}

	private String resolveName(String supplied, PeriodType type, int ordinal) {
		if (supplied != null && !supplied.isBlank()) {
			return supplied.trim();
		}
		return mapper.generateName(type, ordinal);
	}
}
