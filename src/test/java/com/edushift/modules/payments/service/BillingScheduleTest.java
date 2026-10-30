package com.edushift.modules.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.payments.entity.Subscription;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BillingScheduleTest {

	private final ZoneId zone = ZoneId.of("America/Lima");

	@Test
	void dateInMonthClampsDay31InFebruary() {
		assertThat(BillingSchedule.dateInMonth(YearMonth.of(2026, 2), 31))
				.isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(BillingSchedule.dateInMonth(YearMonth.of(2028, 2), 31))
				.isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(BillingSchedule.dateInMonth(YearMonth.of(2026, 4), 31))
				.isEqualTo(LocalDate.of(2026, 4, 30));
		assertThat(BillingSchedule.dateInMonth(YearMonth.of(2026, 1), 31))
				.isEqualTo(LocalDate.of(2026, 1, 31));
	}

	@Test
	void nextMonthlyOnOrAfterUsesSameDayNextMonth() {
		LocalDate from = LocalDate.of(2026, 3, 20);
		assertThat(BillingSchedule.nextMonthlyOnOrAfter(from, 15))
				.isEqualTo(LocalDate.of(2026, 4, 15));
		assertThat(BillingSchedule.nextMonthlyOnOrAfter(LocalDate.of(2026, 3, 10), 15))
				.isEqualTo(LocalDate.of(2026, 3, 15));
	}

	@Test
	void advanceMonthlyPreservesDay31AsEndOfMonth() {
		LocalDate current = LocalDate.of(2026, 1, 31);
		var next = BillingSchedule.advance(
				Subscription.BillingPeriod.MONTHLY, current, 31, null, zone);
		assertThat(LocalDate.ofInstant(next, zone)).isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void advanceAnnualPreservesMonthAndDay() {
		LocalDate current = LocalDate.of(2026, 3, 15);
		var next = BillingSchedule.advance(
				Subscription.BillingPeriod.ANNUAL, current, 15, 3, zone);
		assertThat(LocalDate.ofInstant(next, zone)).isEqualTo(LocalDate.of(2027, 3, 15));
	}
}
