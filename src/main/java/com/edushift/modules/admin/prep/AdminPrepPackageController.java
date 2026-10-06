package com.edushift.modules.admin.prep;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin download of the personalized school data-prep ZIP.
 */
@RestController
@RequestMapping("/admin/prep-package")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Prep Package",
		description = "Download personalized school data-prep ZIP (SUPER_ADMIN)")
public class AdminPrepPackageController {

	private final PrepPackageService prepPackageService;

	@GetMapping
	@Operation(summary = "Download school data-prep ZIP",
			description = "PDF guide + annex + checklist + Excel templates, personalized with schoolName.")
	public ResponseEntity<byte[]> download(
			@RequestParam(required = false) String schoolName,
			@RequestParam(required = false, defaultValue = "PRODUCTION") String variant
	) {
		PrepPackageVariant resolved;
		try {
			resolved = PrepPackageVariant.fromParam(variant);
		}
		catch (IllegalArgumentException ex) {
			throw new com.edushift.shared.exception.BadRequestException(
					"INVALID_PREP_VARIANT", ex.getMessage());
		}
		PrepPackageDownload pack = prepPackageService.build(schoolName, resolved);
		String fileName = pack.fileName();
		String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
		String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
				.contentType(MediaType.parseMediaType("application/zip"))
				.contentLength(pack.bytes().length)
				.body(pack.bytes());
	}
}
