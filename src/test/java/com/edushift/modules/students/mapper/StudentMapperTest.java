package com.edushift.modules.students.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StudentMapperTest {

	private final StudentMapper mapper = new StudentMapper();

	// ===========================================================================
	// toListItem / toResponse
	// ===========================================================================

	@Nested
	@DisplayName("read-side projections")
	class ReadSide {

		@Test
		@DisplayName("toListItem ships only the lean columns the table cells render")
		void listItemIsLean() {
			Student s = newStudent();

			StudentListItem item = mapper.toListItem(s);

			assertThat(item.publicUuid()).isEqualTo(s.getPublicUuid());
			assertThat(item.fullName()).isEqualTo("Ada Lovelace Byron");
			assertThat(item.documentType()).isEqualTo(DocumentType.DNI);
			assertThat(item.email()).isEqualTo("ada@acme.test");
			assertThat(item.enrollmentStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
		}

		@Test
		@DisplayName("toResponse includes a defensive copy of metadata (mutating DTO must not affect entity)")
		void responseMetadataIsDefensive() {
			Student s = newStudent();
			s.getMetadata().put("allergies", "peanuts");

			StudentResponse response = mapper.toResponse(s);
			response.metadata().put("injected", "value");

			assertThat(s.getMetadata()).doesNotContainKey("injected");
			assertThat(response.metadata()).containsEntry("allergies", "peanuts");
		}

		@Test
		@DisplayName("fullName tolerates a missing secondLastName")
		void fullNameWithoutSecondLast() {
			Student s = newStudent();
			s.setSecondLastName(null);

			assertThat(mapper.toResponse(s).fullName()).isEqualTo("Ada Lovelace");
		}
	}

	// ===========================================================================
	// fromCreate
	// ===========================================================================

	@Nested
	@DisplayName("fromCreate — request → entity")
	class FromCreate {

		@Test
		@DisplayName("trims string fields and applies safe defaults for optional enums")
		void appliesDefaults() {
			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "  12345678  ",
					"  Ada  ", "  Lovelace  ",
					null, null, null,    // secondLastName / birthDate / gender
					null, null, null,    // email / phone / address
					null, null, null     // enrollmentStatus / enrollmentDate / metadata
			);

			Student s = mapper.fromCreate(request);

			assertThat(s.getDocumentNumber()).isEqualTo("12345678");
			assertThat(s.getFirstName()).isEqualTo("Ada");
			assertThat(s.getLastName()).isEqualTo("Lovelace");
			assertThat(s.getGender()).isEqualTo(Gender.NOT_SPECIFIED);
			assertThat(s.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.PENDING);
			assertThat(s.getMetadata()).isEmpty();
			assertThat(s.getEmail()).isNull();
		}

		@Test
		@DisplayName("blank optional fields are normalised to null")
		void blankOptionalsBecomeNull() {
			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace",
					"   ", null, null,
					"   ", "   ", "   ",
					null, null, null
			);

			Student s = mapper.fromCreate(request);

			assertThat(s.getSecondLastName()).isNull();
			assertThat(s.getEmail()).isNull();
			assertThat(s.getPhone()).isNull();
			assertThat(s.getAddress()).isNull();
		}

		@Test
		@DisplayName("metadata is copied (not aliased) on creation")
		void metadataIsCopied() {
			Map<String, Object> incoming = new HashMap<>();
			incoming.put("allergies", "peanuts");

			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace",
					null, null, null,
					null, null, null,
					null, null, incoming);

			Student s = mapper.fromCreate(request);

			assertThat(s.getMetadata()).containsEntry("allergies", "peanuts");
			incoming.put("injected", "value");
			assertThat(s.getMetadata()).doesNotContainKey("injected");
		}
	}

	// ===========================================================================
	// applyUpdate
	// ===========================================================================

	@Nested
	@DisplayName("applyUpdate — partial merge")
	class ApplyUpdate {

		@Test
		@DisplayName("null patch and all-null patch are no-ops")
		void nullPatchIsNoOp() {
			Student s = newStudent();

			mapper.applyUpdate(null, s);
			mapper.applyUpdate(allNullPatch(), s);

			assertThat(s.getFirstName()).isEqualTo("Ada");
			assertThat(s.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
		}

		@Test
		@DisplayName("non-null fields are trimmed and applied")
		void appliesPatch() {
			Student s = newStudent();

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, null,
					"  Augusta  ", null,
					null, null, null,
					null, null, null,
					EnrollmentStatus.GRADUATED,
					null, null);

			mapper.applyUpdate(patch, s);

			assertThat(s.getFirstName()).isEqualTo("Augusta");
			assertThat(s.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.GRADUATED);
		}

		@Test
		@DisplayName("blank email / phone / address clears the column")
		void blankClearsNullableField() {
			Student s = newStudent();
			s.setEmail("ada@acme.test");
			s.setPhone("+1 111 111 1111");
			s.setAddress("Some address");

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, null, null, null, null, null, null,
					"   ", "   ", "   ",
					null, null, null);

			mapper.applyUpdate(patch, s);

			assertThat(s.getEmail()).isNull();
			assertThat(s.getPhone()).isNull();
			assertThat(s.getAddress()).isNull();
		}

		@Test
		@DisplayName("metadata replaces wholesale (not deep-merge)")
		void metadataReplaces() {
			Student s = newStudent();
			s.getMetadata().put("allergies", "peanuts");
			s.getMetadata().put("bloodType", "O+");

			Map<String, Object> newMeta = new HashMap<>();
			newMeta.put("bloodType", "AB-");

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, null, null, null, null, null, null,
					null, null, null,
					null, null, newMeta);

			mapper.applyUpdate(patch, s);

			assertThat(s.getMetadata()).containsOnlyKeys("bloodType");
			assertThat(s.getMetadata().get("bloodType")).isEqualTo("AB-");
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static Student newStudent() {
		Student s = new Student();
		setIdViaReflection(s, UUID.randomUUID());
		s.setPublicUuid(UUID.randomUUID());
		s.setDocumentType(DocumentType.DNI);
		s.setDocumentNumber("12345678");
		s.setFirstName("Ada");
		s.setLastName("Lovelace");
		s.setSecondLastName("Byron");
		s.setEmail("ada@acme.test");
		s.setBirthDate(LocalDate.of(1815, 12, 10));
		s.setGender(Gender.FEMALE);
		s.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		s.setEnrollmentDate(LocalDate.of(2026, 3, 1));
		s.setMetadata(new HashMap<>());
		return s;
	}

	private static UpdateStudentRequest allNullPatch() {
		return new UpdateStudentRequest(
				null, null, null, null, null, null, null,
				null, null, null, null, null, null);
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
