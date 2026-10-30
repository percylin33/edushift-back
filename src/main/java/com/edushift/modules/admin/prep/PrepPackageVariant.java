package com.edushift.modules.admin.prep;

/**
 * Scope of the school data-prep pack delivered by Super Admin.
 */
public enum PrepPackageVariant {
	/** Field-test minimum: few sections, optional branding, no payment concepts. */
	PILOT,
	/** Full go-live: complete rosters, branding, payment concepts, D-Day checklist. */
	PRODUCTION;

	public static PrepPackageVariant fromParam(String raw) {
		if (raw == null || raw.isBlank()) {
			return PRODUCTION;
		}
		String key = raw.trim().toUpperCase();
		if ("PILOT".equals(key) || "PILOTO".equals(key)) {
			return PILOT;
		}
		if ("PRODUCTION".equals(key) || "PRODUCCION".equals(key)) {
			return PRODUCTION;
		}
		throw new IllegalArgumentException(
				"variant must be PILOT or PRODUCTION (got: " + raw + ")");
	}

	/** Spanish label shown inside the PDFs and LEEME. */
	public String labelEs() {
		return this == PILOT ? "Piloto" : "Producción";
	}

	/** ASCII file segment so zip entry names stay without accents. */
	public String fileLabel() {
		return this == PILOT ? "Piloto" : "Produccion";
	}
}
