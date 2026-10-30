package com.edushift.modules.students.provision;

import com.edushift.modules.auth.entity.EmailSource;
import java.util.UUID;

/**
 * One credential row returned once after mass provision (CRED-7).
 * {@code temporaryPassword} is plaintext only in the provision response / sheet job —
 * never persisted on {@code users}.
 */
public record ProvisionCredentialRow(
		String role,
		String fullName,
		String documentType,
		String documentNumber,
		String email,
		String temporaryPassword,
		EmailSource emailSource,
		String status,
		UUID studentPublicUuid,
		UUID guardianPublicUuid
) {}
