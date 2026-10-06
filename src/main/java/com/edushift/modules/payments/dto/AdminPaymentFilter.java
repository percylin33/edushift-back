package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Payment;

/**
 * Immutable filter for {@code GET /admin/payments} (DEBT-11-PAY-1).
 *
 * <p>{@code search} is trimmed and capped at 80 chars so the JPQL
 * {@code CONCAT} never receives an oversized pattern.</p>
 */
public record AdminPaymentFilter(
		Payment.Status status,
		Payment.Provider provider,
		String search
) {
	private static final int SEARCH_MAX = 80;

	public AdminPaymentFilter {
		if (search != null) {
			String trimmed = search.trim();
			if (trimmed.isEmpty()) {
				search = null;
			} else if (trimmed.length() > SEARCH_MAX) {
				search = trimmed.substring(0, SEARCH_MAX);
			} else {
				search = trimmed;
			}
		}
	}

	public static AdminPaymentFilter of(
			Payment.Status status,
			Payment.Provider provider,
			String search) {
		return new AdminPaymentFilter(status, provider, search);
	}
}
