package com.edushift.modules.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Utility for building secure httpOnly cookies for token storage.
 *
 * <p>All cookies are:
 * <ul>
 *   <li>{@code HttpOnly} — not accessible via JavaScript (XSS protection)</li>
 *   <li>{@code Secure} — only sent over HTTPS</li>
 *   <li>{@code SameSite=Strict} — CSRF protection</li>
 * </ul>
 */
@Component
public class CookieHelper {

	@Value("${app.security.cookie.domain:}")
	private String cookieDomain;

	@Value("${app.security.cookie.secure:true}")
	private boolean secure;

	@Value("${app.security.cookie.same-site:Strict}")
	private String sameSite;

	/**
	 * Creates a secure httpOnly cookie for the access token.
	 */
	public ResponseCookie createAccessTokenCookie(String token, long maxAgeSeconds) {
		return buildCookie("edushift_access_token", token, maxAgeSeconds);
	}

	/**
	 * Creates a secure httpOnly cookie for the refresh token.
	 */
	public ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
		return buildCookie("edushift_refresh_token", token, maxAgeSeconds);
	}

	/**
	 * Creates a cookie that clears the access token (logout).
	 */
	public ResponseCookie clearAccessTokenCookie() {
		return buildCookie("edushift_access_token", "", 0);
	}

	/**
	 * Creates a cookie that clears the refresh token (logout).
	 */
	public ResponseCookie clearRefreshTokenCookie() {
		return buildCookie("edushift_refresh_token", "", 0);
	}

	/**
	 * Extracts a cookie value from the request.
	 */
	public static String getCookieValue(jakarta.servlet.http.HttpServletRequest request, String name) {
		if (request.getCookies() == null) return null;
		for (Cookie cookie : request.getCookies()) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
		var builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite(sameSite)
				.path("/")
				.maxAge(maxAgeSeconds);

		if (cookieDomain != null && !cookieDomain.isBlank()) {
			builder.domain(cookieDomain);
		}

		return builder.build();
	}
}
