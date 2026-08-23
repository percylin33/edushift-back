package com.edushift.modules.students.mapper;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianProfileResponse;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.users.entity.UserInvitation;
import com.edushift.modules.users.repository.UserInvitationRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Guardian} + {@link StudentGuardian} pairs to
 * {@link GuardianResponse} and materialises a fresh {@link Guardian}
 * from an {@link AddGuardianRequest} when needed.
 *
 * <p>The relationship metadata lives on the link, the contact data
 * lives on the guardian — the response collapses both into one DTO so
 * the frontend has a single object to render in a table cell.
 */
@Component
@RequiredArgsConstructor
public class StudentGuardianMapper {

	private final UserInvitationRepository invitationRepository;

	public GuardianResponse toResponse(StudentGuardian link) {
		Guardian g = link.getGuardian();
		return new GuardianResponse(
				link.getPublicUuid(),
				g.getPublicUuid(),
				g.getDocumentType(),
				g.getDocumentNumber(),
				g.getFirstName(),
				g.getLastName(),
				g.fullName(),
				g.getEmail(),
				g.getPhone(),
				g.getOccupation(),
				link.getRelationship(),
				link.isPrimaryContact(),
				link.isCanPickupStudent(),
				g.getUserId(),
				pendingInvitationUuid(g)
		);
	}

	public GuardianProfileResponse toProfileResponse(Guardian g) {
		return new GuardianProfileResponse(
				g.getPublicUuid(),
				g.getDocumentType(),
				g.getDocumentNumber(),
				g.getFirstName(),
				g.getLastName(),
				g.fullName(),
				g.getEmail(),
				g.getPhone(),
				g.getOccupation(),
				g.getUserId()
		);
	}

	private UUID pendingInvitationUuid(Guardian g) {
		if (g.getUserId() != null || g.getEmail() == null || g.getEmail().isBlank()) {
			return null;
		}
		return invitationRepository.findActivePendingByEmail(g.getEmail(), Instant.now())
				.map(UserInvitation::getPublicUuid)
				.orElse(null);
	}

	/**
	 * Materialises a brand-new {@link Guardian} from an add request.
	 * The service uses this only when the document-based lookup didn't
	 * find an existing guardian.
	 */
	public Guardian newGuardianFromRequest(AddGuardianRequest request) {
		Guardian g = new Guardian();
		g.setDocumentType(request.documentType());
		g.setDocumentNumber(request.documentNumber().trim());
		g.setFirstName(request.firstName().trim());
		g.setLastName(request.lastName().trim());
		g.setEmail(blankToNull(request.email()));
		g.setPhone(blankToNull(request.phone()));
		g.setOccupation(blankToNull(request.occupation()));
		return g;
	}

	private static String blankToNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
