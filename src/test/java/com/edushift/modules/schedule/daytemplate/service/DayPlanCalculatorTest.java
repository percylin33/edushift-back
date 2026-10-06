package com.edushift.modules.schedule.daytemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import com.edushift.shared.exception.BadRequestException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DayPlanCalculator")
class DayPlanCalculatorTest {

	@Test
	void computeSuggestedPeriodsSkipsRecess() {
		List<DayPlanCalculator.Interval> hard = List.of(
				new DayPlanCalculator.Interval(LocalTime.of(10, 15), LocalTime.of(10, 35)));
		List<SuggestedPeriodItem> periods = DayPlanCalculator.computeSuggestedPeriods(
				LocalTime.of(8, 0),
				LocalTime.of(13, 0),
				45,
				hard);
		assertThat(periods).isNotEmpty();
		assertThat(periods.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
		assertThat(periods.get(0).endTime()).isEqualTo(LocalTime.of(8, 45));
		assertThat(periods.stream().noneMatch(p ->
				p.startTime().isBefore(LocalTime.of(10, 35))
						&& p.endTime().isAfter(LocalTime.of(10, 15)))).isTrue();
	}

	@Test
	void computeSuggestedPeriodsExtendsPeriodBeforeRecessAndLunch() {
		List<DayPlanCalculator.Interval> hard = List.of(
				new DayPlanCalculator.Interval(LocalTime.of(10, 45), LocalTime.of(11, 0)),
				new DayPlanCalculator.Interval(LocalTime.of(13, 0), LocalTime.of(13, 45)));
		List<SuggestedPeriodItem> periods = DayPlanCalculator.computeSuggestedPeriods(
				LocalTime.of(7, 30),
				LocalTime.of(15, 15),
				45,
				hard);
		assertThat(periods).anyMatch(p ->
				p.startTime().equals(LocalTime.of(9, 45)) && p.endTime().equals(LocalTime.of(10, 45)));
		assertThat(periods).anyMatch(p ->
				p.startTime().equals(LocalTime.of(11, 45)) && p.endTime().equals(LocalTime.of(13, 0)));
		assertThat(periods.stream().noneMatch(p ->
				p.startTime().equals(LocalTime.of(10, 30)) && p.endTime().equals(LocalTime.of(10, 45)))).isTrue();
		assertThat(periods.stream().noneMatch(p ->
				p.startTime().equals(LocalTime.of(12, 30)) && p.endTime().equals(LocalTime.of(13, 0)))).isTrue();
	}

	@Test
	void validateDayWindowRejectsInverted() {
		assertThatThrownBy(() -> DayPlanCalculator.validateDayWindow(
				LocalTime.of(13, 0), LocalTime.of(8, 0), 45))
				.isInstanceOf(BadRequestException.class);
	}
}
