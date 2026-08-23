package com.edushift.modules.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
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

@DisplayName("Student/guardian invite + accept auto-link userId")
class StudentPortalInviteAcceptFlowIT extends IntegrationTest {

	private static final String STUDENTS_BASE = "/v1/students";
	private static final String INVITATIONS_BASE = "/v1/users/invitations";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String ADMIN_EMAIL = "admin@portal-invite.test";
	private static final String ADMIN_PASSWORD = "AdminInvite-1!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private PlatformPlanRepository platformPlanRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private GuardianRepository guardianRepository;
	@Autowired private StudentGuardianRepository linkRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) {
			tx = new TransactionTemplate(txManager);
		}
		return tx;
	}

	@Test
	@DisplayName("invite student → accept → students.user_id = public_uuid")
	void studentInviteFlow() throws Exception {
		Tenant tenant = createTenant("it-st-inv-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);
		Student student = seedStudent(tenant, "51111111", "lucia-flow@acme.test");

		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> inviteResp = doPost(
				STUDENTS_BASE + "/" + student.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);
		assertThat(inviteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

		JsonNode envelope = objectMapper.readTree(inviteResp.getBody());
		String token = envelope.get("data").get("invitationToken").asText();
		assertThat(token).isNotBlank();

		String acceptBody = """
				{"token":"%s","password":"NewStud-9!"}""".formatted(token);
		ResponseEntity<String> acceptResp = rest.exchange(
				INVITATIONS_BASE + "/accept", HttpMethod.POST,
				new HttpEntity<>(acceptBody, jsonHeaders()), String.class);
		assertThat(acceptResp.getStatusCode())
				.as("accept body=%s", acceptResp.getBody())
				.isEqualTo(HttpStatus.CREATED);

		Student reloaded = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> studentRepository.findByPublicUuid(student.getPublicUuid())
						.orElseThrow()));
		assertThat(reloaded.getUserId()).isNotNull();

		User linkedUser = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> userRepository.findByPublicUuid(reloaded.getUserId())
						.orElseThrow()));
		assertThat(linkedUser.hasRole(UserRole.STUDENT)).isTrue();
		assertThat(linkedUser.getEmail()).isEqualTo("lucia-flow@acme.test");
	}

	@Test
	@DisplayName("invite guardian → accept → guardians.user_id = public_uuid")
	void guardianInviteFlow() throws Exception {
		Tenant tenant = createTenant("it-g-inv-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);
		Student student = seedStudent(tenant, "52222222", "kid-flow@acme.test");
		Guardian guardian = seedGuardian(tenant, student, "43333333", "madre-flow@acme.test");

		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> inviteResp = doPost(
				STUDENTS_BASE + "/" + student.getPublicUuid()
						+ "/guardians/" + guardian.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);
		assertThat(inviteResp.getStatusCode())
				.as("invite body=%s", inviteResp.getBody())
				.isEqualTo(HttpStatus.OK);

		String token = objectMapper.readTree(inviteResp.getBody())
				.get("data").get("invitationToken").asText();

		String acceptBody = """
				{"token":"%s","password":"NewParent-9!"}""".formatted(token);
		ResponseEntity<String> acceptResp = rest.exchange(
				INVITATIONS_BASE + "/accept", HttpMethod.POST,
				new HttpEntity<>(acceptBody, jsonHeaders()), String.class);
		assertThat(acceptResp.getStatusCode())
				.as("accept body=%s", acceptResp.getBody())
				.isEqualTo(HttpStatus.CREATED);

		Guardian reloaded = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> guardianRepository.findByPublicUuid(guardian.getPublicUuid())
						.orElseThrow()));
		assertThat(reloaded.getUserId()).isNotNull();

		User linkedUser = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> userRepository.findByPublicUuid(reloaded.getUserId())
						.orElseThrow()));
		assertThat(linkedUser.hasRole(UserRole.PARENT)).isTrue();
		assertThat(linkedUser.getEmail()).isEqualTo("madre-flow@acme.test");
	}

	@Test
	@DisplayName("invite refused when student already linked → 409 STUDENT_ALREADY_HAS_USER")
	void inviteRefusedWhenStudentLinked() throws Exception {
		Tenant tenant = createTenant("it-st-linked-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);

		User otherUser = TenantContext.runAs(tenant.getId(),
				() -> tx().execute(s -> {
					User u = new User();
					u.setEmail("already-student@x.test");
					u.setPasswordHash(passwordEncoder.encode("Password-1!"));
					u.setFirstName("X");
					u.setLastName("Y");
					u.setStatus(UserStatus.ACTIVE);
					u.setEmailVerified(true);
					u.setMfaEnabled(false);
					u.addRole(UserRole.STUDENT);
					return userRepository.saveAndFlush(u);
				}));
		Student student = TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Student st = new Student();
			st.setDocumentType(DocumentType.DNI);
			st.setDocumentNumber("54444444");
			st.setFirstName("Already");
			st.setLastName("Linked");
			st.setEmail("already-student@x.test");
			st.setUserId(otherUser.getPublicUuid());
			st.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
			return studentRepository.saveAndFlush(st);
		}));

		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> response = doPost(
				STUDENTS_BASE + "/" + student.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("STUDENT_ALREADY_HAS_USER");
	}

	@Test
	@DisplayName("invite student without email → 422 STUDENT_EMAIL_REQUIRED")
	void inviteRefusedWithoutEmail() throws Exception {
		Tenant tenant = createTenant("it-st-noemail-");
		createAdmin(tenant, ADMIN_EMAIL, ADMIN_PASSWORD);
		Student student = seedStudent(tenant, "55555555", null);

		AuthResponse adminLogin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> response = doPost(
				STUDENTS_BASE + "/" + student.getPublicUuid() + "/invite",
				adminLogin.accessToken(), null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(response.getBody()).contains("STUDENT_EMAIL_REQUIRED");
	}

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
		UUID planId = platformPlanRepository.findByCode(TenantPlan.TRIAL.name())
				.map(PlatformPlan::getId)
				.orElseGet(() -> platformPlanRepository.findByIsActiveTrueOrderBySortOrder()
						.stream()
						.findFirst()
						.map(PlatformPlan::getId)
						.orElseThrow(() -> new IllegalStateException(
								"No platform_plans rows; did Flyway V54 run?")));
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		t.setPlan(TenantPlan.TRIAL);
		t.setPlanId(planId);
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

	private Student seedStudent(Tenant tenant, String docNumber, String email) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Student st = new Student();
			st.setDocumentType(DocumentType.DNI);
			st.setDocumentNumber(docNumber);
			st.setFirstName("Lucia");
			st.setLastName("Flow");
			st.setEmail(email);
			st.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
			return studentRepository.saveAndFlush(st);
		}));
	}

	private Guardian seedGuardian(Tenant tenant, Student student, String docNumber, String email) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Guardian g = new Guardian();
			g.setDocumentType(DocumentType.DNI);
			g.setDocumentNumber(docNumber);
			g.setFirstName("Maria");
			g.setLastName("Flow");
			g.setEmail(email);
			Guardian saved = guardianRepository.saveAndFlush(g);

			StudentGuardian link = new StudentGuardian();
			link.setStudent(studentRepository.findByPublicUuid(student.getPublicUuid()).orElseThrow());
			link.setGuardian(saved);
			link.setRelationship(RelationshipType.MOTHER);
			link.setPrimaryContact(true);
			link.setCanPickupStudent(true);
			linkRepository.saveAndFlush(link);
			return saved;
		}));
	}
}
