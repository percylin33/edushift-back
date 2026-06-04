package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body shared by {@code POST /auth/refresh} and {@code POST /auth/logout}.
 * <p>
 * The refresh token travels in the request body (not as a cookie) because
 * Sprint 1's frontend is a SPA that stores tokens in {@code localStorage}.
 * Sprint 2 will introduce the option of httpOnly secure cookies for clients
 * that opt in.
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code refreshToken} must not be blank — empty bodies are caught at
 *       the validation layer with a structured 400, instead of leaking
 *       through to a confusing 401 from {@code AuthService}.</li>
 *   <li>{@code @Size(max = 4096)} guards against abusive payloads. A signed
 *       JWT with our claim shape rarely exceeds 1 KB, so 4 KB is a
 *       generous cap that still rejects e.g. someone POSTing a 1 MB blob.</li>
 * </ul>
 *
 * <p>The {@code toString} masks the token so it never lands in request logs.
 *
 * @param refreshToken raw refresh JWT issued by {@code POST /auth/login}
 *                     (or rotated by {@code POST /auth/refresh})
 */
public record RefreshTokenRequest(

		@NotBlank(message = "Refresh token is required")
		@Size(max = 4096, message = "Refresh token is too large")
		String refreshToken
) {

	/** Defensive {@code toString} — never log the raw token. */
	@Override
	public String toString() {
		return "RefreshTokenRequest[refreshToken=***]";
	}

}
