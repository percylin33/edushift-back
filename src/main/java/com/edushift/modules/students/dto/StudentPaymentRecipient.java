package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import java.util.UUID;

/** Minimal student identity returned to staff issuing payment invoices. */
public record StudentPaymentRecipient(
		UUID publicUuid,
		DocumentType documentType,
		String documentNumber,
		String fullName
) {}
