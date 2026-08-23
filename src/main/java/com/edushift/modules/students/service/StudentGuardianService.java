package com.edushift.modules.students.service;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianProfileResponse;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.dto.InviteGuardianResponse;
import com.edushift.modules.students.dto.LinkGuardianUserRequest;
import com.edushift.modules.students.dto.UpdateGuardianLinkRequest;
import com.edushift.modules.students.entity.DocumentType;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped management of the student↔guardian link.
 *
 * <h3>Guardrails</h3>
 * <ul>
 *   <li><strong>Primary-contact protection.</strong> A student must
 *       always have at least one active primary-contact guardian once
 *       any has been assigned. The service refuses
 *       {@link #unlinkGuardian} and primary-toggle updates that would
 *       leave the student without one.</li>
 *   <li><strong>Sibling sharing.</strong> Adding a guardian whose
 *       {@code (documentType, documentNumber)} already matches an
 *       existing guardian in the tenant reuses that row instead of
 *       creating a duplicate.</li>
 *   <li><strong>Duplicate-link guard.</strong> The same
 *       {@code (student, guardian)} pair cannot be linked twice while
 *       active — surfaced as
 *       {@code 409 GUARDIAN_ALREADY_LINKED}.</li>
 * </ul>
 */
public interface StudentGuardianService {

	List<GuardianResponse> listGuardians(UUID studentPublicUuid);

	/**
	 * Lookup a guardian profile by document within the current tenant.
	 * Used by the add-guardian form to pre-fill fields when the person
	 * already exists (e.g. linked to a sibling).
	 */
	GuardianProfileResponse lookupByDocument(DocumentType documentType, String documentNumber);

	GuardianResponse addGuardian(UUID studentPublicUuid, AddGuardianRequest request);

	GuardianResponse updateLink(UUID studentPublicUuid, UUID guardianPublicUuid,
			UpdateGuardianLinkRequest request);

	void unlinkGuardian(UUID studentPublicUuid, UUID guardianPublicUuid);

	/**
	 * Verifies the authenticated parent owns the requested student scope.
	 * Implementations must resolve both public identifiers inside the current tenant.
	 */
	void assertParentLinkedToStudent(UUID parentUserPublicUuid, UUID studentPublicUuid);

	/** Rescue: bind an existing PARENT user to this guardian. */
	GuardianResponse linkUser(UUID studentPublicUuid, UUID guardianPublicUuid,
			LinkGuardianUserRequest request);

	/** Invite the guardian to claim a PARENT portal (metadata.guardianId). */
	InviteGuardianResponse invite(UUID studentPublicUuid, UUID guardianPublicUuid);

}
