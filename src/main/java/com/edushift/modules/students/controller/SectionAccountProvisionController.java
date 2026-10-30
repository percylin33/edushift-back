package com.edushift.modules.students.controller;

import com.edushift.modules.students.provision.ProvisionHandoutDownload;
import com.edushift.modules.students.provision.ProvisionSectionAccountsRequest;
import com.edushift.modules.students.provision.ProvisionSectionAccountsResponse;
import com.edushift.modules.students.provision.SectionAccountProvisionService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRED-7 mass account provision for a classroom section.
 *
 * <p>Lives under {@code .controller} so {@link com.edushift.config.WebConfiguration}
 * applies the {@code /v1} path prefix.
 *
 * <ul>
 *   <li>{@code POST /academic/sections/{uuid}/provision-accounts}</li>
 *   <li>{@code GET /academic/sections/provision-accounts/{jobUuid}/handout.zip}</li>
 *   <li>{@code GET /academic/sections/provision-accounts/{jobUuid}/sheet.xlsx}</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/academic/sections")
@Tag(name = "Section account provision",
		description = "Mass portal accounts + printable credential sheet (CRED-7)")
public class SectionAccountProvisionController {

	private final SectionAccountProvisionService provisionService;

	@PostMapping("/{sectionPublicUuid}/provision-accounts")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('"
			+ LmsAuthorities.USERS_PROVISION_SECTION + "')")
	@Operation(summary = "Provision portal accounts for active students in a section",
			description = "Creates STUDENT (and optional PARENT) users with temporary "
					+ "passwords. Hybrid email: roster email when present, else synthetic. "
					+ "Does not send mail. Returns credentials once + jobPublicUuid for sheet download.")
	public ApiResponse<ProvisionSectionAccountsResponse> provision(
			@PathVariable UUID sectionPublicUuid,
			@Valid @RequestBody(required = false) ProvisionSectionAccountsRequest request
	) {
		return ApiResponse.ok(provisionService.provision(sectionPublicUuid, request));
	}

	@GetMapping("/provision-accounts/{jobPublicUuid}/sheet.xlsx")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('"
			+ LmsAuthorities.USERS_PROVISION_SECTION + "')")
	@Operation(summary = "Download admin credential sheet (xlsx)",
			description = "Secondary export for ops. TTL ~20 minutes. Passwords not stored in DB.")
	public ResponseEntity<byte[]> downloadSheet(@PathVariable UUID jobPublicUuid) {
		byte[] bytes = provisionService.downloadSheet(jobPublicUuid);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"cuentas-seccion.xlsx\"")
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(bytes);
	}

	@GetMapping("/provision-accounts/{jobPublicUuid}/handout.zip")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('"
			+ LmsAuthorities.USERS_PROVISION_SECTION + "')")
	@Operation(summary = "Download family credential handouts (ZIP)",
			description = "ZIP with folder Accesos - {grado/sección} and one PDF per student. TTL ~20 minutes.")
	public ResponseEntity<byte[]> downloadHandoutZip(@PathVariable UUID jobPublicUuid) {
		ProvisionHandoutDownload download = provisionService.downloadHandoutZip(jobPublicUuid);
		String fileName = download.fileName() == null || download.fileName().isBlank()
				? "Accesos.zip"
				: download.fileName();
		// ASCII fallback + RFC 5987 for accents / em-dash in section names.
		String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
		String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
				.replace("+", "%20");
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
				.contentType(MediaType.parseMediaType("application/zip"))
				.body(download.bytes());
	}
}
