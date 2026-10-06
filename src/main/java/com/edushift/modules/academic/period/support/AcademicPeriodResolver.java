package com.edushift.modules.academic.period.support;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the academic period that should be treated as "current" for
 * student-facing surfaces. Mirrors the FE helper
 * {@code resolveCurrentAcademicPeriod} in {@code period.model.ts}.
 */
@Component
@RequiredArgsConstructor
public class AcademicPeriodResolver {

	private final AcademicYearRepository academicYearRepository;
	private final AcademicPeriodRepository academicPeriodRepository;

	public Optional<AcademicPeriod> resolveCurrentPeriod() {
		return resolveCurrentPeriod(LocalDate.now());
	}

	public Optional<AcademicPeriod> resolveCurrentPeriod(LocalDate today) {
		return academicYearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE)
				.map(year -> resolveCurrent(academicPeriodRepository.findAllByYear(year), today));
	}

	/**
	 * Picks the period containing {@code today}; if none, the nearest
	 * upcoming; otherwise the most recent past period.
	 */
	public static AcademicPeriod resolveCurrent(List<AcademicPeriod> periods, LocalDate today) {
		if (periods == null || periods.isEmpty()) {
			return null;
		}

		List<AcademicPeriod> containing = periods.stream()
				.filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
				.sorted(Comparator
						.comparing(AcademicPeriod::getPeriodType)
						.thenComparing(AcademicPeriod::getOrdinal))
				.toList();
		if (!containing.isEmpty()) {
			return containing.getFirst();
		}

		Optional<AcademicPeriod> upcoming = periods.stream()
				.filter(p -> p.getStartDate().isAfter(today))
				.min(Comparator.comparing(AcademicPeriod::getStartDate));
		if (upcoming.isPresent()) {
			return upcoming.get();
		}

		return periods.stream()
				.filter(p -> p.getEndDate().isBefore(today))
				.max(Comparator.comparing(AcademicPeriod::getEndDate))
				.orElse(null);
	}
}
