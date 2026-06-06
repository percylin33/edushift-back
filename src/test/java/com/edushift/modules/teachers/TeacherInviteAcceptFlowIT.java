package com.edushift.modules.teachers;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end IT for the teachers/invitations integration (Sprint 4 / BE-4.6).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>TENANT_ADMIN creates a teacher with no linked user.</li>
 *   <li>Admin calls {@code POST /v1/teachers/{uuid}/invite} → invitation
 *       row exists with metadata.teacherId set.</li>
 *   <li>Anonymous {@code POST /v1/users/invitations/accept} with the
 *       token + a fresh password → 201, new User + linked teacher.</li>
 *   <li>Read the teacher: {@code teacher.userId} is now set to the
 *       new User's id.</li>
 * </ol>
 */
@DisplayName("Teacher invite + accept end-to-end (auto-link userId)")
class TeacherInviteAcceptFlowIT extends IntegrationTest {

	private static final String TEACHERS_BASE = "/v1/teachers";
	private static final String INVITATIONS_BASE = "/v1/users/invitations";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String ADMIN_EMAIL = "admin@invite-flow.test";
	private static final String ADMIN_PASSWORD = "AdminInvite-1!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private UserInvitationRepository invitationRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	@Test
	@DisplayName("invite → accept → teacher.userId populated atomically")
	void fullInviteFlow() throws Exception {
		Tenant tenant = createTenant("it-flow-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);
		Teacher teacher = seedTeacher(tenant, "33333333", "ada-flow@acme.test");

		// 1) login as admin
		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);

		// 2) call invite
		ResponseEntity<String> inviteResp = doPost(
				TEACHERS_BASE + "/" + teacher.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);
		assertThat(inviteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

		JsonNode envelope = objectMapper.readTree(inviteResp.getBody());
		String token = envelope.get("data").get("invitationToken").asText();
		assertThat(token).isNotBlank();

		// 3) accept invitation anonymously (public path)
		String acceptBody = """
				{"token":"%s","password":"NewTeach-9!"}""".formatted(token);
		ResponseEntity<String> acceptResp = rest.exchange(
				INVITATIONS_BASE + "/accept", HttpMethod.POST,
				new HttpEntity<>(acceptBody, jsonHeaders()), String.class);

		assertThat(acceptResp.getStatusCode())
				.as("accept body=%s", acceptResp.getBody())
				.isEqualTo(HttpStatus.CREATED);

		// 4) verify the new user exists and has TEACHER role + that the
		//    teacher row got userId populated.
		Teacher reloaded = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> teacherRepository.findByPublicUuid(teacher.getPublicUuid())
						.orElseThrow()));
		assertThat(reloaded.getUserId()).as("teacher.userId must be populated by listener")
				.isNotNull();

		User linkedUser = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> userRepository.findById(reloaded.getUserId())
						.orElseThrow()));
		assertThat(linkedUser.hasRole(UserRole.TEACHER)).isTrue();
		assertThat(linkedUser.getEmail()).isEqualTo("ada-flow@acme.test");
	}

	@Test
	@DisplayName("invite refused when teacher already linked → 409 TEACHER_ALREADY_HAS_USER")
	void inviteRefusedWhenLinked() throws Exception {
		Tenant tenant = createTenant("it-flow-linked-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);

		// Create a linked teacher (userId set in advance)
		User otherUser = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> {
					User u = new User();
					u.setEmail("already-linked@x.test");
					u.setPasswordHash(passwordEncoder.encode("Password-1!"));
					u.setFirstName("X");
					u.setLastName("Y");
					u.setStatus(UserStatus.ACTIVE);
					u.setEmailVerified(true);
					u.setMfaEnabled(false);
					u.addRole(UserRole.TEACHER);
					return userRepository.saveAndFlush(u);
				}));
		Teacher teacher = TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Teacher t = new Teacher();
			t.setDocumentType(DocumentType.DNI);
			t.setDocumentNumber("44444444");
			t.setFirstName("Already");
			t.setLastName("Linked");
			t.setEmail("already-linked@x.test");
			t.setUserId(otherUser.getId());
			t.setEmploymentStatus(EmploymentStatus.ACTIVE);
			return teacherRepository.saveAndFlush(t);
		}));

		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> response = doPost(
				TEACHERS_BASE + "/" + teacher.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("TEACHER_ALREADY_HAS_USER");
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private AuthResponse login(String slug, String email, String password) throws Exception {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		ResponseEntity<String> response = rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		HttpEntity<String> entity = body != null
				? new HttpEntity<>(body, headers)
				: new HttpEntity<>(headers);
		return rest.exchange(path, HttpMethod.POST, entity, String.class);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private void createAdmin(Tenant tenant, String email, String rawPassword) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("It");
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.TENANT_ADMIN);
					return userRepository.saveAndFlush(user);
				}));
	}

	private Teacher seedTeacher(Tenant tenant, String docNumber, String email) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Teacher t = new Teacher();
			t.setDocumentType(DocumentType.DNI);
			t.setDocumentNumber(docNumber);
			t.setFirstName("Ada");
			t.setLastName("Lovelace");
			t.setEmail(email);
			t.setEmploymentStatus(EmploymentStatus.ACTIVE);
			return teacherRepository.saveAndFlush(t);
		}));
	}
}
