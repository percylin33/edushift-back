package com.edushift.modules.payments.controller;

import com.edushift.shared.security.LmsAuthorities;

/** Shared method-security expressions for the tenant payment console. */
final class PaymentAccessExpressions {

	private static final String ADMIN_OR_LEGACY =
			"hasRole('TENANT_ADMIN') or hasAuthority('LMS_PAYMENT_ADMIN')";

	static final String READ = ADMIN_OR_LEGACY + " or hasAuthority('PAYMENTS:READ')";
	static final String CONCEPTS_READ = ADMIN_OR_LEGACY
			+ " or hasAuthority('PAYMENTS:READ')"
			+ " or hasAuthority('PAYMENTS:ISSUE')"
			+ " or hasAuthority('" + LmsAuthorities.PAYMENT_CONCEPTS_MANAGE + "')";
	static final String RECEIVE = ADMIN_OR_LEGACY + " or hasAuthority('PAYMENTS:RECEIVE')";
	static final String RECONCILE = ADMIN_OR_LEGACY + " or hasAuthority('PAYMENTS:RECONCILE')";
	static final String ISSUE = ADMIN_OR_LEGACY
			+ " or hasAuthority('" + LmsAuthorities.PAYMENTS_ISSUE + "')";
	static final String CONCEPTS_MANAGE = ADMIN_OR_LEGACY
			+ " or hasAuthority('" + LmsAuthorities.PAYMENT_CONCEPTS_MANAGE + "')";
	static final String REFUND = ADMIN_OR_LEGACY + " or hasAuthority('PAYMENTS:REFUND')";
	static final String EXPORT = ADMIN_OR_LEGACY + " or hasAuthority('PAYMENTS:EXPORT')";
	static final String MANAGE = ADMIN_OR_LEGACY;

	private PaymentAccessExpressions() {}
}
