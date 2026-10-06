package com.edushift.shared.branding;

import java.io.IOException;
import java.io.InputStream;

/**
 * Raster de la marca EduShift en el classpath ({@code resources/branding}).
 * OpenPDF no incrusta SVG; el front sirve los mismos PNG desde
 * {@code public/branding}.
 */
public final class PlatformBrandAssets {

	public static final String HORIZONTAL_LOGO = "branding/logos/horizontal.png";
	public static final String LOGO_PRINCIPAL = "branding/logos/logo-principal.png";
	public static final String LOGO_ON_DARK = "branding/logos/fondo-oscuro.png";
	public static final String MARK = "branding/icons/isotipo.png";
	public static final String ICON = "branding/icons/icono.png";
	public static final String ICON_SIMPLE = "branding/icons/icono-simple.png";
	public static final String ICON_SIMPLE_SMALL = "branding/icons/icono-simple-pequeno.png";
	public static final String SEAL = "branding/seals/sello.png";

	private PlatformBrandAssets() {
	}

	public static byte[] read(String classpathLocation) {
		if (classpathLocation == null || classpathLocation.isBlank()) {
			return null;
		}
		try (InputStream in = PlatformBrandAssets.class.getClassLoader()
				.getResourceAsStream(classpathLocation)) {
			if (in == null) {
				return null;
			}
			byte[] bytes = in.readAllBytes();
			return bytes.length == 0 ? null : bytes;
		}
		catch (IOException ex) {
			return null;
		}
	}

	/** Isotipo cuadrado para encabezados de PDF cuando el colegio no tiene logo. */
	public static byte[] markBytes() {
		return read(MARK);
	}
}
