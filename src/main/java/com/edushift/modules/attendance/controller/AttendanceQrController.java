package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import com.edushift.modules.attendance.service.AttendanceQrService;
import com.edushift.modules.attendance.service.AttendanceQrService.IssuedQr;
import com.edushift.modules.attendance.service.QrRenderer;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.NotAcceptableStatusException;

/**
 * REST adapter for student QR credentials (Sprint 6 / BE-6.3).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>QR endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/students/{publicUuid}/attendance-qr</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>image/svg+xml | image/png (binary)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/students/{publicUuid}/attendance-qr/info</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link ApiResponse}&lt;{@link AttendanceQrInfo}&gt; (or null)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/students/{publicUuid}/attendance-qr/rotate</td>
 *       <td>TENANT_ADMIN only</td>
 *       <td>{@link ApiResponse}&lt;{@link AttendanceQrInfo}&gt;</td></tr>
 * </table>
 *
 * <h3>Important client contract</h3>
 * <strong>{@code GET /attendance-qr} mutates the active row</strong>:
 * every call mints a fresh JWT and revokes the previous one. The FE
 * therefore separates "preview / status" (handled by
 * {@code GET /attendance-qr/info} — read-only) from "generate +
 * download printable" (this endpoint). See {@link AttendanceQrService}
 * for the rationale.
 */
@Slf4j
@RestController
@RequestMapping("/v1/students/{publicUuid}/attendance-qr")
@RequiredArgsConstructor
@Tag(name = "AttendanceQr",
		description = "Lifecycle of a student's printable QR credential. "
				+ "GET issues a new credential and invalidates any "
				+ "previous one; POST /rotate is its admin-only "
				+ "explicit twin.")
public class AttendanceQrController {

	private static final String FORMAT_QUERY_SVG = "svg";
	private static final String FORMAT_QUERY_PNG = "png";

	private final AttendanceQrService qrService;
	private final QrRenderer qrRenderer;

	// =====================================================================
	// GET /attendance-qr  (binary)
	// =====================================================================

	@GetMapping(produces = {
			MediaType.IMAGE_PNG_VALUE,
			"image/svg+xml",
			MediaType.APPLICATION_OCTET_STREAM_VALUE
	})
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Issue and render the student's QR credential",
			description = "Returns the binary QR image (PNG by default; "
					+ "SVG when Accept: image/svg+xml or ?format=svg). "
					+ "Mutates the active row: every call mints a fresh "
					+ "JWT and revokes the previous one. Use "
					+ "GET /attendance-qr/info for a read-only status "
					+ "check.")
	public ResponseEntity<byte[]> getQr(
			@PathVariable UUID publicUuid,
			@RequestParam(name = "format", required = false)
			@Parameter(description = "Override the Accept header. "
					+ "Allowed: 'svg' or 'png'.")
			String format,
			HttpServletRequest request) {

		MediaType chosen = resolveFormat(request, format);

		IssuedQr issued = qrService.getOrIssueQr(publicUuid);
		byte[] body = MediaType.IMAGE_PNG.equals(chosen)
				? qrRenderer.renderPng(issued.jwt())
				: qrRenderer.renderSvg(issued.jwt());

		log.info("[attendance-qr] rendered -- student={} format={} qrIssuedAt={}",
				publicUuid, chosen, issued.info().issuedAt());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(chosen);
		headers.setContentLength(body.length);
		headers.setCacheControl(CacheControl.noStore().mustRevalidate());
		// Stash the iat in a custom header so a client that wants to
		// detect "the QR I just received was already revoked elsewhere"
		// can branch without an extra info call.
		headers.add("X-Attendance-Qr-Issued-At",
				issued.info().issuedAt().toString());

		return new ResponseEntity<>(body, headers, 200);
	}

	// =====================================================================
	// GET /attendance-qr/info  (JSON, read-only)
	// =====================================================================

	@GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Read-only metadata for the student's active QR",
			description = "Returns issuedAt of the current active row, "
					+ "or 200 with data=null if the alumno has never been "
					+ "issued a QR. Never mutates the row — use this in "
					+ "the FE to decide between 'Generar credencial' and "
					+ "'Reimprimir credencial' before calling GET.")
	public ResponseEntity<ApiResponse<AttendanceQrInfo>> getInfo(
			@PathVariable UUID publicUuid) {
		AttendanceQrInfo info = qrService.getInfo(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(info));
	}

	// =====================================================================
	// POST /attendance-qr/rotate  (JSON)
	// =====================================================================

	@PostMapping(value = "/rotate", produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Admin-only: explicitly rotate the student's QR",
			description = "Same DB effect as GET /attendance-qr but "
					+ "exposed as a deliberate action (the FE wires this "
					+ "to 'Credencial perdida' confirmations). The audit "
					+ "trail in BE-6.4 distinguishes this from the "
					+ "implicit rotation triggered by a render.")
	public ResponseEntity<ApiResponse<AttendanceQrInfo>> rotate(
			@PathVariable UUID publicUuid) {
		IssuedQr issued = qrService.rotate(publicUuid);
		log.info("[attendance-qr] rotated -- student={} qrIssuedAt={}",
				publicUuid, issued.info().issuedAt());
		return ResponseEntity.ok(ApiResponse.ok(issued.info(), "QR rotated"));
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/**
	 * Resolve the response Content-Type from (in order):
	 * <ol>
	 *   <li>Explicit {@code ?format=svg|png} query parameter.</li>
	 *   <li>{@code Accept} header content-negotiation against
	 *       {@code image/svg+xml} and {@code image/png}.</li>
	 *   <li>{@code image/png} default for {@code Accept: &#42;/&#42;}
	 *       (or no accept).</li>
	 * </ol>
	 *
	 * @throws NotAcceptableStatusException 406 when neither the query
	 *         override nor the Accept header can be satisfied.
	 */
	private MediaType resolveFormat(HttpServletRequest request, String formatOverride) {
		if (formatOverride != null && !formatOverride.isBlank()) {
			String trimmed = formatOverride.trim().toLowerCase();
			if (FORMAT_QUERY_SVG.equals(trimmed)) {
				return MediaType.valueOf("image/svg+xml");
			}
			if (FORMAT_QUERY_PNG.equals(trimmed)) {
				return MediaType.IMAGE_PNG;
			}
			throw new NotAcceptableStatusException(
					"Unsupported ?format='" + formatOverride
							+ "'. Allowed: svg, png");
		}

		String accept = request.getHeader(HttpHeaders.ACCEPT);
		if (accept == null || accept.isBlank()) {
			return MediaType.IMAGE_PNG;
		}
		List<MediaType> requested = MediaType.parseMediaTypes(accept);
		MediaType.sortBySpecificityAndQuality(requested);
		MediaType svg = MediaType.valueOf("image/svg+xml");
		for (MediaType mt : requested) {
			if (mt.includes(svg)) {
				return svg;
			}
			if (mt.includes(MediaType.IMAGE_PNG)) {
				return MediaType.IMAGE_PNG;
			}
			if (mt.equalsTypeAndSubtype(MediaType.ALL)) {
				return MediaType.IMAGE_PNG;
			}
		}
		throw new NotAcceptableStatusException(
				"Accept header must include image/svg+xml or image/png");
	}
}
