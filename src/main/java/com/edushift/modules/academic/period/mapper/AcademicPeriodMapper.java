package com.edushift.modules.academic.period.mapper;

import com.edushift.modules.academic.period.dto.AcademicPeriodListItem;
import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link AcademicPeriod}. Same convention as
 * the rest of the codebase (no MapStruct).
 *
 * <p>Also exposes {@link #generateName(PeriodType, int)} for the
 * service to auto-fill {@code name} when callers omit it.</p>
 */
@Component
public class AcademicPeriodMapper {

	private static final String[] ROMAN_UNITS = {
			"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"
	};
	private static final String[] ROMAN_TENS = {
			"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"
	};

	public AcademicPeriodResponse toResponse(AcademicPeriod period) {
		return new AcademicPeriodResponse(
				period.getPublicUuid(),
				period.getAcademicYear().getPublicUuid(),
				period.getAcademicYear().getName(),
				period.getPeriodType(),
				period.getOrdinal(),
				period.getName(),
				period.getStartDate(),
				period.getEndDate(),
				period.getCreatedAt(),
				period.getUpdatedAt()
		);
	}

	public AcademicPeriodListItem toListItem(AcademicPeriod period) {
		return new AcademicPeriodListItem(
				period.getPublicUuid(),
				period.getAcademicYear().getPublicUuid(),
				period.getPeriodType(),
				period.getOrdinal(),
				period.getName(),
				period.getStartDate(),
				period.getEndDate()
		);
	}

	/**
	 * Generates the auto-name for a period when callers omit it.
	 * Format: {@code "<roman_ordinal> <PeriodType.displayLabel>"}.
	 *
	 * @throws IllegalArgumentException if {@code ordinal} is &lt; 1 or &gt; 99
	 *         (well above any realistic school setup of 4 bimestres).
	 */
	public String generateName(PeriodType type, int ordinal) {
		if (ordinal < 1 || ordinal > 99) {
			throw new IllegalArgumentException(
					"Ordinal " + ordinal + " out of supported range [1..99]");
		}
		String roman = toRoman(ordinal);
		return roman + " " + type.displayLabel();
	}

	private static String toRoman(int n) {
		int tens = n / 10;
		int units = n % 10;
		return ROMAN_TENS[tens] + ROMAN_UNITS[units];
	}
}
