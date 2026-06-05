package com.edushift.modules.students.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.dto.UpdateGuardianLinkRequest;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.mapper.StudentGuardianMapper;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentGuardianServiceImplTest {

	@Mock private StudentRepository studentRepository;
	@Mock private GuardianRepository guardianRepository;
	@Mock private StudentGuardianRepository linkRepository;
	@Spy private StudentGuardianMapper mapper = new StudentGuardianMapper();

	@InjectMocks private StudentGuardianServiceImpl service;

	// ===========================================================================
	// listGuardians
	// ===========================================================================

	@Nested
	@DisplayName("listGuardians")
	class ListGuardians {

		@Test
		@DisplayName("happy path — fetches active links and projects each one")
		void happyPath() {
			Student student = newStudent();
			Guardian g1 = newGuardian("12345678", "Anna");
			Guardian g2 = newGuardian("87654321", "Bob");
			StudentGuardian l1 = newLink(student, g1, RelationshipType.MOTHER, true);
			StudentGuardian l2 = newLink(student, g2, RelationshipType.FATHER, false);
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(linkRepository.findActiveByStudentId(student.getId())).thenReturn(List.of(l1, l2));

			List<GuardianResponse> result = service.listGuardians(student.getPublicUuid());

			assertThat(result).hasSize(2);
			assertThat(result).extracting(GuardianResponse::firstName)
					.containsExactly("Anna", "Bob");
		}

		@Test
		@DisplayName("unknown student → ResourceNotFoundException")
		void unknownStudent() {
			UUID id = UUID.randomUUID();
			when(studentRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listGuardians(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// addGuardian
	// ===========================================================================

	@Nested
	@DisplayName("addGuardian")
	class AddGuardian {

		@Test
		@DisplayName("guardian doesn't exist yet — creates a new one and links it")
		void createsNewGuardian() {
			Student student = newStudent();
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByDocumentTypeAndDocumentNumber(
					DocumentType.DNI, "11111111")).thenReturn(Optional.empty());
			when(guardianRepository.save(any(Guardian.class))).thenAnswer(inv -> {
				Guardian g = inv.getArgument(0);
				setIdViaReflection(g, UUID.randomUUID());
				g.setPublicUuid(UUID.randomUUID());
				return g;
			});
			when(linkRepository.findActivePair(any(), any())).thenReturn(Optional.empty());
			when(linkRepository.save(any(StudentGuardian.class))).thenAnswer(inv -> {
				StudentGuardian sg = inv.getArgument(0);
				setIdViaReflection(sg, UUID.randomUUID());
				sg.setPublicUuid(UUID.randomUUID());
				return sg;
			});

			AddGuardianRequest request = new AddGuardianRequest(
					DocumentType.DNI, "11111111",
					"Anna", "Lovelace",
					"anna@acme.test", "+51 999", "Engineer",
					RelationshipType.MOTHER, true, true);

			GuardianResponse response = service.addGuardian(student.getPublicUuid(), request);

			assertThat(response.firstName()).isEqualTo("Anna");
			assertThat(response.relationship()).isEqualTo(RelationshipType.MOTHER);
			assertThat(response.isPrimaryContact()).isTrue();
			verify(guardianRepository, times(1)).save(any(Guardian.class));
		}

		@Test
		@DisplayName("guardian already exists (sibling sharing) — reuses the row, no save() of guardian")
		void siblingSharing() {
			Student student = newStudent();
			Guardian existing = newGuardian("11111111", "Anna");
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByDocumentTypeAndDocumentNumber(
					DocumentType.DNI, "11111111")).thenReturn(Optional.of(existing));
			when(linkRepository.findActivePair(student.getId(), existing.getId()))
					.thenReturn(Optional.empty());
			when(linkRepository.save(any(StudentGuardian.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			AddGuardianRequest request = new AddGuardianRequest(
					DocumentType.DNI, "11111111",
					"Anna", "Lovelace",
					null, null, null,
					RelationshipType.MOTHER, false, false);

			service.addGuardian(student.getPublicUuid(), request);

			verify(guardianRepository, never()).save(any(Guardian.class));
			ArgumentCaptor<StudentGuardian> captor = ArgumentCaptor.forClass(StudentGuardian.class);
			verify(linkRepository).save(captor.capture());
			assertThat(captor.getValue().getGuardian()).isSameAs(existing);
		}

		@Test
		@DisplayName("duplicate active link → ConflictException(GUARDIAN_ALREADY_LINKED)")
		void duplicateLinkRejected() {
			Student student = newStudent();
			Guardian existing = newGuardian("11111111", "Anna");
			StudentGuardian existingLink = newLink(student, existing, RelationshipType.MOTHER, true);
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByDocumentTypeAndDocumentNumber(any(), any()))
					.thenReturn(Optional.of(existing));
			when(linkRepository.findActivePair(student.getId(), existing.getId()))
					.thenReturn(Optional.of(existingLink));

			AddGuardianRequest request = new AddGuardianRequest(
					DocumentType.DNI, "11111111",
					"Anna", "Lovelace", null, null, null,
					RelationshipType.MOTHER, false, false);

			assertThatThrownBy(() -> service.addGuardian(student.getPublicUuid(), request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("GUARDIAN_ALREADY_LINKED"));

			verify(linkRepository, never()).save(any(StudentGuardian.class));
		}
	}

	// ===========================================================================
	// updateLink
	// ===========================================================================

	@Nested
	@DisplayName("updateLink")
	class UpdateLink {

		@Test
		@DisplayName("toggles canPickupStudent — applies and persists")
		void togglesPickup() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.MOTHER, true);

			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));
			when(linkRepository.save(any(StudentGuardian.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			GuardianResponse response = service.updateLink(
					student.getPublicUuid(), guardian.getPublicUuid(),
					new UpdateGuardianLinkRequest(null, null, true));

			assertThat(response.canPickupStudent()).isTrue();
			assertThat(link.isCanPickupStudent()).isTrue();
		}

		@Test
		@DisplayName("flipping isPrimaryContact off when no other primary exists → BusinessException(LAST_PRIMARY_CONTACT)")
		void cannotRemoveLastPrimary() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.MOTHER, true);

			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));
			when(linkRepository.countActivePrimaryContacts(student.getId())).thenReturn(1L);

			assertThatThrownBy(() -> service.updateLink(
					student.getPublicUuid(), guardian.getPublicUuid(),
					new UpdateGuardianLinkRequest(null, false, null)))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("LAST_PRIMARY_CONTACT"));

			verify(linkRepository, never()).save(any(StudentGuardian.class));
		}

		@Test
		@DisplayName("flipping isPrimaryContact off when other primaries exist → succeeds")
		void canRemovePrimaryWhenOthersExist() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.MOTHER, true);

			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));
			when(linkRepository.countActivePrimaryContacts(student.getId())).thenReturn(2L);
			when(linkRepository.save(any(StudentGuardian.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			service.updateLink(student.getPublicUuid(), guardian.getPublicUuid(),
					new UpdateGuardianLinkRequest(null, false, null));

			assertThat(link.isPrimaryContact()).isFalse();
		}
	}

	// ===========================================================================
	// unlinkGuardian
	// ===========================================================================

	@Nested
	@DisplayName("unlinkGuardian")
	class UnlinkGuardian {

		@Test
		@DisplayName("happy path — soft-deletes the link")
		void happyPath() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.MOTHER, false);
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));

			service.unlinkGuardian(student.getPublicUuid(), guardian.getPublicUuid());

			verify(linkRepository).delete(link);
		}

		@Test
		@DisplayName("unlinking the only primary contact → LAST_PRIMARY_CONTACT")
		void cannotRemoveLastPrimary() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.MOTHER, true);
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));
			when(linkRepository.countActivePrimaryContacts(student.getId())).thenReturn(1L);

			assertThatThrownBy(() -> service.unlinkGuardian(
					student.getPublicUuid(), guardian.getPublicUuid()))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("LAST_PRIMARY_CONTACT"));

			verify(linkRepository, never()).delete(any(StudentGuardian.class));
		}

		@Test
		@DisplayName("non-primary link unlinks freely even when count is 1 (it's not the primary one)")
		void nonPrimaryUnlinksFreely() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			StudentGuardian link = newLink(student, guardian, RelationshipType.GRANDPARENT, false);
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.of(link));

			service.unlinkGuardian(student.getPublicUuid(), guardian.getPublicUuid());

			verify(linkRepository).delete(link);
			// countActivePrimaryContacts is never consulted because the
			// candidate is not primary.
			verify(linkRepository, never()).countActivePrimaryContacts(any());
		}

		@Test
		@DisplayName("unknown link → ResourceNotFoundException")
		void unknownLink() {
			Student student = newStudent();
			Guardian guardian = newGuardian("11111111", "Anna");
			when(studentRepository.findByPublicUuid(student.getPublicUuid()))
					.thenReturn(Optional.of(student));
			when(guardianRepository.findByPublicUuid(guardian.getPublicUuid()))
					.thenReturn(Optional.of(guardian));
			when(linkRepository.findActivePair(student.getId(), guardian.getId()))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.unlinkGuardian(
					student.getPublicUuid(), guardian.getPublicUuid()))
					.isInstanceOf(ResourceNotFoundException.class);
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
		s.setDocumentNumber("99999999");
		s.setFirstName("Kid");
		s.setLastName("LastName");
		s.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		s.setMetadata(new HashMap<>());
		return s;
	}

	private static Guardian newGuardian(String document, String firstName) {
		Guardian g = new Guardian();
		setIdViaReflection(g, UUID.randomUUID());
		g.setPublicUuid(UUID.randomUUID());
		g.setDocumentType(DocumentType.DNI);
		g.setDocumentNumber(document);
		g.setFirstName(firstName);
		g.setLastName("Lovelace");
		return g;
	}

	private static StudentGuardian newLink(Student s, Guardian g,
			RelationshipType type, boolean primary) {
		StudentGuardian sg = new StudentGuardian();
		setIdViaReflection(sg, UUID.randomUUID());
		sg.setPublicUuid(UUID.randomUUID());
		sg.setStudent(s);
		sg.setGuardian(g);
		sg.setRelationship(type);
		sg.setPrimaryContact(primary);
		return sg;
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
