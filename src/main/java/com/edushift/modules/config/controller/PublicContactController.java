package com.edushift.modules.config.controller;

import com.edushift.config.PublicContactProperties;
import com.edushift.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only public contact addresses for the website and legal pages. */
@RestController
@RequestMapping("/public/contact")
@RequiredArgsConstructor
public class PublicContactController {

	private final PublicContactProperties contacts;

	@GetMapping
	public ResponseEntity<ApiResponse<ContactEmailsResponse>> getPublicContactEmails() {
		return ResponseEntity.ok(ApiResponse.ok(new ContactEmailsResponse(
				contacts.supportEmail(), contacts.privacyEmail(), contacts.legalEmail())));
	}

	public record ContactEmailsResponse(String supportEmail, String privacyEmail, String legalEmail) {}
}
