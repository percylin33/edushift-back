package com.edushift.modules.evaluations.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Internal map for LITERAL_CNEB averages: AD=4, A=3, B=2, C=1.
 * The numeric result is turned back into a letter before it is shown
 * or used as a category contribution.
 */
public final class CnebLiteralScale {

	private static final BigDecimal AD = new BigDecimal("3.50");
	private static final BigDecimal A = new BigDecimal("2.50");
	private static final BigDecimal B = new BigDecimal("1.50");

	private CnebLiteralScale() {
	}

	public static BigDecimal points(String literal) {
		if (literal == null || literal.isBlank()) {
			return null;
		}
		return switch (literal.trim().toUpperCase()) {
			case "AD" -> new BigDecimal("4");
			case "A" -> new BigDecimal("3");
			case "B" -> new BigDecimal("2");
			case "C" -> new BigDecimal("1");
			default -> null;
		};
	}

	/** 3.50–4.00 AD, 2.50–3.49 A, 1.50–2.49 B, otherwise C. */
	public static String toLiteral(BigDecimal average) {
		if (average == null) {
			return null;
		}
		BigDecimal value = average.setScale(2, RoundingMode.HALF_UP);
		if (value.compareTo(AD) >= 0) {
			return "AD";
		}
		if (value.compareTo(A) >= 0) {
			return "A";
		}
		if (value.compareTo(B) >= 0) {
			return "B";
		}
		return "C";
	}
}
