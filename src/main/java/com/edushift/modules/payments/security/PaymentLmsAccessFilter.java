package com.edushift.modules.payments.security;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.payments.service.PaymentAccessService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Soft LMS gate: blocks selected student/parent LMS mutations when
 * payment policy lock is enabled and there is overdue debt.
 * Never applies to auth, payments, family, announcements, or notifications.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class PaymentLmsAccessFilter extends OncePerRequestFilter {

	private static final List<Pattern> RESTRICTED = List.of(
			Pattern.compile(".*/attempts/[^/]+/submit$", Pattern.CASE_INSENSITIVE),
			Pattern.compile(".*/quizzes/[^/]+/attempts$", Pattern.CASE_INSENSITIVE),
			Pattern.compile(".*/tasks/[^/]+/submissions$", Pattern.CASE_INSENSITIVE),
			Pattern.compile(".*/submissions$", Pattern.CASE_INSENSITIVE),
			Pattern.compile(".*/ai/chat/sessions/[^/]+/messages$", Pattern.CASE_INSENSITIVE),
			Pattern.compile(".*/reports/[^/]+/download$", Pattern.CASE_INSENSITIVE)
	);

	private final PaymentAccessService paymentAccessService;
	private final CurrentUserProvider currentUserProvider;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
		String method = request.getMethod() == null ? "" : request.getMethod();
		boolean mutating = "POST".equalsIgnoreCase(method)
				|| "PUT".equalsIgnoreCase(method)
				|| "PATCH".equalsIgnoreCase(method)
				|| "DELETE".equalsIgnoreCase(method)
				|| ("GET".equalsIgnoreCase(method) && uri.contains("/download"));
		if (!mutating || !isRestricted(uri)) {
			filterChain.doFilter(request, response);
			return;
		}
		UserRole role = currentUserProvider.currentUserRole().orElse(null);
		if (role != UserRole.STUDENT && role != UserRole.PARENT) {
			filterChain.doFilter(request, response);
			return;
		}
		UUID userId = currentUserProvider.currentUserId().orElse(null);
		if (userId == null) {
			filterChain.doFilter(request, response);
			return;
		}
		// This filter sits in the security chain, before TenantFilter binds
		// the request tenant. The policy lookup needs that binding.
		UUID tenantId = currentUserProvider.currentTenantId().orElse(null);
		boolean boundHere = tenantId != null && TenantContext.current().isEmpty();
		if (boundHere) {
			TenantContext.set(tenantId);
		}
		try {
			paymentAccessService.assertLmsAllowed(userId);
		} catch (BusinessException ex) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.getWriter().write(
					"{\"success\":false,\"error\":{\"code\":\"PAYMENT_LMS_RESTRICTED\",\"message\":\""
							+ ex.getMessage().replace("\"", "'") + "\"}}");
			return;
		} finally {
			if (boundHere) {
				TenantContext.clear();
			}
		}
		filterChain.doFilter(request, response);
	}

	private static boolean isRestricted(String uri) {
		for (Pattern p : RESTRICTED) {
			if (p.matcher(uri).find()) {
				return true;
			}
		}
		return false;
	}
}
