package com.edushift.modules.academic.period.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AcademicPeriodResolverTest {

	@Test
	@DisplayName("resolveCurrent returns the period containing today")
	void containingPeriodWins() {
		AcademicPeriod p1 = period("I Bimestre", 1, "2026-03-01", "2026-04-30");
		AcademicPeriod p2 = period("II Bimestre", 2, "2026-05-01", "2026-06-30");
		AcademicPeriod p3 = period("III Bimestre", 3, "2026-07-01", "2026-08-31");

		AcademicPeriod current = AcademicPeriodResolver.resolveCurrent(
				List.of(p1, p2, p3), LocalDate.parse("2026-07-15"));

		assertThat(current).isSameAs(p3);
	}

	@Test
	@DisplayName("resolveCurrent falls back to upcoming period between terms")
	void upcomingPeriodWhenBetweenTerms() {
		AcademicPeriod p1 = period("I Bimestre", 1, "2026-03-01", "2026-04-30");
		AcademicPeriod p2 = period("II Bimestre", 2, "2026-05-01", "2026-06-30");

		AcademicPeriod current = AcademicPeriodResolver.resolveCurrent(
				List.of(p1, p2), LocalDate.parse("2026-05-01"));

		assertThat(current).isSameAs(p2);
	}

	private static AcademicPeriod period(String name, int ordinal, String start, String end) {
		AcademicPeriod period = new AcademicPeriod();
		period.setName(name);
		period.setOrdinal(ordinal);
		period.setPeriodType(PeriodType.BIMESTRE);
		period.setStartDate(LocalDate.parse(start));
		period.setEndDate(LocalDate.parse(end));
		return period;
	}
}
