package com.edushift.modules.auth.security;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * {@link org.springframework.security.core.Authentication Authentication}
 * implementation produced by {@link JwtAuthenticationFilter} after a bearer
 * access token has been verified.
 *
 * <p>The token is "pre-authenticated" by JWT signature verification, so we
 * eagerly call {@code setAuthenticated(true)} in the constructor (this is the
 * standard pattern for JWT filters and matches Spring Security's own
 * {@code BearerTokenAuthenticationToken}).
 *
 * <p>{@link #getName()} intentionally returns the user's {@code publicUuid}
 * as a string, because:
 * <ul>
 *   <li>{@code AuthServiceImpl.currentUser()} parses {@code getName()} as a
 *       UUID and looks the user up via {@code findByPublicUuid}.</li>
 *   <li>It keeps {@code MDC} / log lines compact (a UUID instead of a record
 *       {@code toString()}).</li>
 * </ul>
 *
 * <p>The raw bearer token is preserved as the credentials so downstream
 * filters (e.g. token introspection or signed downstream-call propagation in
 * later sprints) can read it without re-parsing the request.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

	private final JwtAuthenticatedPrincipal principal;

	private final String token;

	public JwtAuthenticationToken(JwtAuthenticatedPrincipal principal,
	                              String token,
	                              Collection<? extends GrantedAuthority> authorities) {
		super(authorities == null ? Collections.emptyList() : authorities);
		if (principal == null) {
			throw new IllegalArgumentException("principal must not be null");
		}
		this.principal = principal;
		this.token = token;
		// Signature was already verified by the filter — mark as authenticated
		// directly. AbstractAuthenticationToken#setAuthenticated(true) is
		// allowed only when the constructor that takes authorities is used,
		// which is exactly this one.
		super.setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}

	@Override
	public String getName() {
		return principal.id().toString();
	}

	/**
	 * Block external callers from downgrading the token to "not authenticated".
	 * The only way to invalidate this token is to remove it from the
	 * {@link org.springframework.security.core.context.SecurityContext}.
	 */
	@Override
	public void setAuthenticated(boolean isAuthenticated) {
		if (isAuthenticated) {
			throw new IllegalArgumentException(
					"Cannot set this token to trusted; use the constructor instead");
		}
		super.setAuthenticated(false);
	}

	/**
	 * Avoids leaking the bearer token in logs; only the principal identity
	 * is included in {@code toString()}.
	 */
	@Override
	public String toString() {
		return "JwtAuthenticationToken[principal=" + principal
				+ ", authenticated=" + isAuthenticated()
				+ ", authorities=" + getAuthorities() + "]";
	}

}
