package com.edushift.modules.admissions.dto;

import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.students.dto.GuardianPortalAccessResponse;

/** Academic enrollment is committed independently from the optional fee issuance. */
public record AdmissionEnrollmentCompletionResponse(
		AdmissionApplicationResponse application,
		BillingStatus billingStatus,
		InvoiceResponse enrollmentInvoice,
		GuardianPortalAccessResponse.Status portalAccessStatus
) {
	public enum BillingStatus { NOT_CONFIGURED, PENDING, PAID, ISSUE_FAILED }
}
