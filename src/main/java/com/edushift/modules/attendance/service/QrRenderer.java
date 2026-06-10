package com.edushift.modules.attendance.service;

import com.edushift.shared.exception.BadRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders a string payload as a QR code in PNG (binary) or SVG (XML)
 * format (Sprint 6 / BE-6.3.D).
 *
 * <p>Configurable via three properties:
 * <ul>
 *   <li>{@code edushift.attendance.qr.pixel-size}    — final image size in
 *       pixels (square). Default {@code 256}. Both SVG viewBox and PNG
 *       buffer use the same dimension.</li>
 *   <li>{@code edushift.attendance.qr.error-correction} — one of
 *       {@code L | M | Q | H} (lo-hi). Default {@code M}. Higher levels
 *       waste payload capacity but recover from credential wear/dirt.</li>
 *   <li>{@code edushift.attendance.qr.margin-modules} — quiet-zone
 *       margin in QR <em>modules</em> (the "pixels" in QR speak), not
 *       output pixels. Default {@code 4} per the QR spec. ZXing
 *       multiplies this by the per-module pixel size.</li>
 * </ul>
 *
 * <h3>Why hand-roll the SVG path?</h3>
 * ZXing has no SVG writer in the box. The {@link BitMatrix} exposes
 * a deterministic boolean grid; emitting one {@code <rect>} per dark
 * module produces a perfectly scalable SVG with zero extra deps. The
 * resulting markup is also dirt-cheap to gzip on the wire.
 */
@Slf4j
@Component
public class QrRenderer {

	private static final int MAX_PIXEL_SIZE = 1024;

	private final int pixelSize;
	private final ErrorCorrectionLevel errorCorrection;
	private final int marginModules;
	private final Map<EncodeHintType, Object> hints;

	public QrRenderer(
			@Value("${edushift.attendance.qr.pixel-size:256}") int pixelSize,
			@Value("${edushift.attendance.qr.error-correction:M}")
					String errorCorrection,
			@Value("${edushift.attendance.qr.margin-modules:4}") int marginModules) {
		this.pixelSize = sanitizePixelSize(pixelSize);
		this.errorCorrection = parseErrorCorrection(errorCorrection);
		this.marginModules = Math.max(marginModules, 0);
		this.hints = buildHints(this.errorCorrection, this.marginModules);
		log.info("[qr-renderer] pixelSize={} errorCorrection={} marginModules={}",
				this.pixelSize, this.errorCorrection, this.marginModules);
	}

	// =====================================================================
	// PNG
	// =====================================================================

	/**
	 * @param payload the string to encode (typically a JWT).
	 * @return PNG bytes ready to write to an HTTP response body.
	 */
	public byte[] renderPng(String payload) {
		BitMatrix matrix = encode(payload);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			MatrixToImageWriter.writeToStream(matrix, "PNG", out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException(
					"Failed to render QR payload as PNG", e);
		}
	}

	// =====================================================================
	// SVG
	// =====================================================================

	/**
	 * @param payload the string to encode (typically a JWT).
	 * @return UTF-8 bytes of the SVG document. Suitable for either
	 *         direct response with {@code Content-Type: image/svg+xml}
	 *         or inline embedding (e.g. {@code <img src="data:...">}).
	 */
	public byte[] renderSvg(String payload) {
		BitMatrix matrix = encode(payload);
		String svg = buildSvg(matrix);
		return svg.getBytes(StandardCharsets.UTF_8);
	}

	private String buildSvg(BitMatrix matrix) {
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		StringBuilder sb = new StringBuilder(2048);
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
		sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" ");
		sb.append("width=\"").append(pixelSize).append("\" ");
		sb.append("height=\"").append(pixelSize).append("\" ");
		sb.append("viewBox=\"0 0 ").append(width).append(' ').append(height)
				.append("\" shape-rendering=\"crispEdges\">");
		sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
		sb.append("<path fill=\"#000000\" d=\"");
		// Run-length emit: collapse consecutive dark modules in the same
		// row into a single horizontal rectangle ("h" path command). This
		// shrinks the SVG ~5x compared to emitting one <rect> per module.
		for (int y = 0; y < height; y++) {
			int runStart = -1;
			for (int x = 0; x < width; x++) {
				boolean dark = matrix.get(x, y);
				if (dark && runStart < 0) {
					runStart = x;
				}
				else if (!dark && runStart >= 0) {
					appendRect(sb, runStart, y, x - runStart);
					runStart = -1;
				}
			}
			if (runStart >= 0) {
				appendRect(sb, runStart, y, width - runStart);
			}
		}
		sb.append("\"/></svg>");
		return sb.toString();
	}

	private static void appendRect(StringBuilder sb, int x, int y, int w) {
		sb.append('M').append(x).append(' ').append(y)
				.append("h").append(w).append("v1h-").append(w).append('z');
	}

	// =====================================================================
	// Encoder shared
	// =====================================================================

	private BitMatrix encode(String payload) {
		if (payload == null || payload.isBlank()) {
			throw new IllegalArgumentException("payload must not be blank");
		}
		try {
			return new QRCodeWriter().encode(
					payload, BarcodeFormat.QR_CODE,
					pixelSize, pixelSize, hints);
		}
		catch (WriterException e) {
			throw new IllegalStateException(
					"Failed to encode QR payload: " + e.getMessage(), e);
		}
	}

	private static Map<EncodeHintType, Object> buildHints(
			ErrorCorrectionLevel level, int marginModules) {
		Map<EncodeHintType, Object> map = new EnumMap<>(EncodeHintType.class);
		map.put(EncodeHintType.ERROR_CORRECTION, level);
		map.put(EncodeHintType.MARGIN, marginModules);
		map.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		return map;
	}

	private static int sanitizePixelSize(int requested) {
		if (requested < 64) return 64;
		if (requested > MAX_PIXEL_SIZE) {
			return MAX_PIXEL_SIZE;
		}
		return requested;
	}

	private static ErrorCorrectionLevel parseErrorCorrection(String value) {
		if (value == null || value.isBlank()) {
			return ErrorCorrectionLevel.M;
		}
		try {
			return ErrorCorrectionLevel.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new BadRequestException(
					"INVALID_QR_ERROR_CORRECTION",
					"edushift.attendance.qr.error-correction must be one of L | M | Q | H");
		}
	}
}
