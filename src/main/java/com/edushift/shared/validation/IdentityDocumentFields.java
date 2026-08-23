package com.edushift.shared.validation;

import com.edushift.modules.students.entity.DocumentType;

/** Contract for DTOs that carry a tenant-scoped identity document pair. */
public interface IdentityDocumentFields {

	DocumentType documentType();

	String documentNumber();
}
