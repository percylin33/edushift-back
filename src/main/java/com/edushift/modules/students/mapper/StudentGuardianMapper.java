package com.edushift.modules.students.mapper;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.StudentGuardian;
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
public class StudentGuardianMapper {

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
				link.isCanPickupStudent()
		);
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
