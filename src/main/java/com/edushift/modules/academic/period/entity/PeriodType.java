package com.edushift.modules.academic.period.entity;

/**
 * Academic period taxonomy. The three values cover the most common
 * MINEDU Peru calendar shapes — schools may even mix BIMESTRE
 * (operational) with ANUAL (final averages).
 */
public enum PeriodType {

	/** Four per year (the MINEDU default). */
	BIMESTRE("Bimestre"),

	/** Three per year. */
	TRIMESTRE("Trimestre"),

	/** Single period spanning the whole year (used for final averages). */
	ANUAL("Anual");

	private final String displayLabel;

	PeriodType(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	/** Capitalised label suitable for the auto-generated period name. */
	public String displayLabel() {
		return displayLabel;
	}
}
