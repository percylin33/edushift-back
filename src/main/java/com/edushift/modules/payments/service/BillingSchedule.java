package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Subscription;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Computes next billing dates from a preferred day-of-month (and month for annual),
 * clamping to the last day when the target month is shorter (e.g. day 31 → Feb 28/29).
 */
public final class BillingSchedule {

	private BillingSchedule() {}

	public static int clampDay(int dayOfMonth) {
		if (dayOfMonth < 1) {
			return 1;
		}
		return Math.min(dayOfMonth, 31);
	}

	public static int clampMonth(int month) {
		if (month < 1) {
			return 1;
		}
		return Math.min(month, 12);
	}

	/** Local date for (yearMonth, preferredDay) clamped to month length. */
	public static LocalDate dateInMonth(YearMonth yearMonth, int preferredDay) {
		int day = Math.min(clampDay(preferredDay), yearMonth.lengthOfMonth());
		return yearMonth.atDay(day);
	}

	/**
	 * Next monthly charge on or after {@code from} (inclusive) for the preferred day.
	 */
	public static LocalDate nextMonthlyOnOrAfter(LocalDate from, int preferredDay) {
		int day = clampDay(preferredDay);
		YearMonth ym = YearMonth.from(from);
		LocalDate candidate = dateInMonth(ym, day);
		if (candidate.isBefore(from)) {
			ym = ym.plusMonths(1);
			candidate = dateInMonth(ym, day);
		}
		return candidate;
	}

	/**
	 * Next annual charge on or after {@code from} for month + day.
	 */
	public static LocalDate nextAnnualOnOrAfter(LocalDate from, int preferredMonth, int preferredDay) {
		int month = clampMonth(preferredMonth);
		int day = clampDay(preferredDay);
		YearMonth ym = YearMonth.of(from.getYear(), month);
		LocalDate candidate = dateInMonth(ym, day);
		if (candidate.isBefore(from)) {
			ym = YearMonth.of(from.getYear() + 1, month);
			candidate = dateInMonth(ym, day);
		}
		return candidate;
	}

	public static Instant toStartOfDay(LocalDate date, ZoneId zone) {
		return date.atStartOfDay(zone).toInstant();
	}

	/**
	 * Advance one period from the current billing local date, preserving preferred day.
	 */
	public static Instant advance(
			Subscription.BillingPeriod period,
			LocalDate currentBillingDate,
			Integer billingDayOfMonth,
			Integer billingMonth,
			ZoneId zone) {
		int day = billingDayOfMonth != null
				? clampDay(billingDayOfMonth)
				: currentBillingDate.getDayOfMonth();
		return switch (period) {
			case MONTHLY -> {
				YearMonth next = YearMonth.from(currentBillingDate).plusMonths(1);
				yield toStartOfDay(dateInMonth(next, day), zone);
			}
			case ANNUAL -> {
				int month = billingMonth != null
						? clampMonth(billingMonth)
						: currentBillingDate.getMonthValue();
				YearMonth next = YearMonth.of(currentBillingDate.getYear() + 1, month);
				yield toStartOfDay(dateInMonth(next, day), zone);
			}
		};
	}
}
