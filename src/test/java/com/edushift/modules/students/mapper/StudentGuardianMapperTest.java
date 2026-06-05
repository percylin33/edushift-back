package com.edushift.modules.students.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.entity.StudentGuardian;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentGuardianMapperTest {

	private final StudentGuardianMapper mapper = new StudentGuardianMapper();

	@Test
	@DisplayName("toResponse — collapses guardian + link into a single DTO")
	void toResponseMergesBothEntities() {
		Guardian g = new Guardian();
		g.setPublicUuid(UUID.randomUUID());
		g.setDocumentType(DocumentType.DNI);
		g.setDocumentNumber("11111111");
		g.setFirstName("Anna");
		g.setLastName("Lovelace");
		g.setEmail("anna@acme.test");
		g.setPhone("+51 999");
		g.setOccupation("Engineer");

		StudentGuardian link = new StudentGuardian();
		link.setPublicUuid(UUID.randomUUID());
		link.setGuardian(g);
		link.setRelationship(RelationshipType.MOTHER);
		link.setPrimaryContact(true);
		link.setCanPickupStudent(true);

		GuardianResponse response = mapper.toResponse(link);

		assertThat(response.linkPublicUuid()).isEqualTo(link.getPublicUuid());
		assertThat(response.guardianPublicUuid()).isEqualTo(g.getPublicUuid());
		assertThat(response.documentType()).isEqualTo(DocumentType.DNI);
		assertThat(response.documentNumber()).isEqualTo("11111111");
		assertThat(response.firstName()).isEqualTo("Anna");
		assertThat(response.lastName()).isEqualTo("Lovelace");
		assertThat(response.fullName()).isEqualTo("Anna Lovelace");
		assertThat(response.email()).isEqualTo("anna@acme.test");
		assertThat(response.phone()).isEqualTo("+51 999");
		assertThat(response.occupation()).isEqualTo("Engineer");
		assertThat(response.relationship()).isEqualTo(RelationshipType.MOTHER);
		assertThat(response.isPrimaryContact()).isTrue();
		assertThat(response.canPickupStudent()).isTrue();
	}

	@Test
	@DisplayName("newGuardianFromRequest — trims required fields and blank-to-null for optionals")
	void newGuardianFromRequestNormalises() {
		AddGuardianRequest request = new AddGuardianRequest(
				DocumentType.CE, "  AB-1234  ",
				"  Anna  ", "  Lovelace  ",
				"   ", "   ", "",
				RelationshipType.GUARDIAN, false, false);

		Guardian g = mapper.newGuardianFromRequest(request);

		assertThat(g.getDocumentType()).isEqualTo(DocumentType.CE);
		assertThat(g.getDocumentNumber()).isEqualTo("AB-1234");
		assertThat(g.getFirstName()).isEqualTo("Anna");
		assertThat(g.getLastName()).isEqualTo("Lovelace");
		assertThat(g.getEmail()).isNull();
		assertThat(g.getPhone()).isNull();
		assertThat(g.getOccupation()).isNull();
	}

	@Test
	@DisplayName("newGuardianFromRequest — keeps non-blank optionals as-is (trimmed)")
	void newGuardianFromRequestKeepsNonBlankOptionals() {
		AddGuardianRequest request = new AddGuardianRequest(
				DocumentType.DNI, "11111111",
				"Anna", "Lovelace",
				"  anna@acme.test  ", " +51 999 ", " Engineer ",
				RelationshipType.MOTHER, true, false);

		Guardian g = mapper.newGuardianFromRequest(request);

		assertThat(g.getEmail()).isEqualTo("anna@acme.test");
		assertThat(g.getPhone()).isEqualTo("+51 999");
		assertThat(g.getOccupation()).isEqualTo("Engineer");
	}
}
