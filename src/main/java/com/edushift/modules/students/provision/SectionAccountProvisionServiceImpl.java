package com.edushift.modules.students.provision;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.entity.EmailSource;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.web.FrontendLinks;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates STUDENT / PARENT users for section roster rows that lack a portal link.
 * Never emails credentials; plaintext passwords exist only in the response / TTL job.
 * Job rows always include family blocks (student + primary tutor) for the PDF handout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectionAccountProvisionServiceImpl implements SectionAccountProvisionService {

	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final GuardianRepository guardianRepository;
	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final ProvisionCredentialJobStore jobStore;
	private final ProvisionAccountSheetExporter sheetExporter;
	private final ProvisionFamilyHandoutPdfExporter handoutPdfExporter;
	private final FrontendLinks frontendLinks;

	@Override
	@Transactional
	public ProvisionSectionAccountsResponse provision(
			UUID sectionPublicUuid,
			ProvisionSectionAccountsRequest request
	) {
		ProvisionSectionAccountsRequest opts = request == null
				? new ProvisionSectionAccountsRequest(true, true)
				: request;
		// Family handout always needs tutors when available.
		boolean includeGuardians = opts.includeGuardiansOrDefault();

		Section section = sectionRepository.findByPublicUuid(sectionPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", sectionPublicUuid));

		Tenant tenant = tenantRepository.findById(TenantContext.currentRequired())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Tenant", TenantContext.currentRequired()));
		String tenantSlug = tenant.getSlug();
		String sectionLabel = buildSectionLabel(section);

		List<StudentEnrollment> enrollments = enrollmentRepository.findActiveBySection(section);
		List<ProvisionCredentialRow> credentials = new ArrayList<>();
		Map<UUID, CreatedAccount> createdGuardians = new HashMap<>();
		int studentsProvisioned = 0;
		int guardiansProvisioned = 0;
		int skippedAlreadyLinked = 0;
		int syntheticEmails = 0;
		int realEmails = 0;

		for (StudentEnrollment enrollment : enrollments) {
			Student student = enrollment.getStudent();
			if (student == null) {
				continue;
			}

			if (student.getUserId() != null) {
				skippedAlreadyLinked++;
				credentials.add(existingRow(UserRole.STUDENT.name(), student, null));
			}
			else {
				CreatedAccount created = createPortalUser(
						student.getFirstName(),
						student.getLastName(),
						student.getSecondLastName(),
						student.getEmail(),
						student.getDocumentNumber(),
						UserRole.STUDENT,
						tenantSlug,
						opts.useSyntheticWhenMissingOrDefault());
				student.setUserId(created.user().getPublicUuid());
				studentRepository.save(student);
				credentials.add(toRow(created, UserRole.STUDENT.name(), student, null));
				studentsProvisioned++;
				if (created.emailSource() == EmailSource.SYNTHETIC) {
					syntheticEmails++;
				}
				else {
					realEmails++;
				}
			}

			if (!includeGuardians) {
				continue;
			}

			Guardian guardian = pickPrimaryGuardian(student.getId());
			if (guardian == null) {
				continue;
			}

			if (guardian.getUserId() != null) {
				credentials.add(existingRow(UserRole.PARENT.name(), student, guardian));
				continue;
			}

			CreatedAccount already = createdGuardians.get(guardian.getId());
			if (already != null) {
				credentials.add(toRow(already, UserRole.PARENT.name(), student, guardian));
				continue;
			}

			CreatedAccount created = createPortalUser(
					guardian.getFirstName(),
					guardian.getLastName(),
					null,
					guardian.getEmail(),
					guardian.getDocumentNumber(),
					UserRole.PARENT,
					tenantSlug,
					opts.useSyntheticWhenMissingOrDefault());
			guardian.setUserId(created.user().getPublicUuid());
			guardianRepository.save(guardian);
			createdGuardians.put(guardian.getId(), created);
			guardiansProvisioned++;
			credentials.add(toRow(created, UserRole.PARENT.name(), student, guardian));
			if (created.emailSource() == EmailSource.SYNTHETIC) {
				syntheticEmails++;
			}
			else {
				realEmails++;
			}
		}

		UUID jobId = jobStore.put(
				sectionLabel,
				academicYearName(section),
				levelName(section),
				credentials);
		log.info("[provision-accounts] section={} students={} guardians={} skipped={} synth={} real={} job={}",
				sectionPublicUuid, studentsProvisioned, guardiansProvisioned,
				skippedAlreadyLinked, syntheticEmails, realEmails, jobId);

		return new ProvisionSectionAccountsResponse(
				jobId,
				section.getPublicUuid(),
				sectionLabel,
				studentsProvisioned,
				guardiansProvisioned,
				skippedAlreadyLinked,
				syntheticEmails,
				realEmails,
				credentials
		);
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] downloadSheet(UUID jobPublicUuid) {
		ProvisionCredentialJobStore.EntryView entry = jobStore.get(jobPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"ProvisionJob", jobPublicUuid));
		try {
			return sheetExporter.toXlsx(entry.sectionName(), entry.rows());
		}
		catch (IOException ex) {
			throw new BadRequestException("SHEET_EXPORT_FAILED",
					"No se pudo generar la hoja de credenciales");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public ProvisionHandoutDownload downloadHandoutZip(UUID jobPublicUuid) {
		ProvisionCredentialJobStore.EntryView entry = jobStore.get(jobPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"ProvisionJob", jobPublicUuid));
		Tenant tenant = tenantRepository.findById(TenantContext.currentRequired())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Tenant", TenantContext.currentRequired()));
		CredentialHandoutSettings handout =
				CredentialHandoutSettings.fromTenantSettings(tenant.getSettings());
		String loginUrl = handout.loginUrlOverride() != null
				? handout.loginUrlOverride()
				: frontendLinks.schoolLogin(tenant.getSlug());
		String logoUrl = brandingLogoUrl(tenant.getBranding());
		byte[] zip = handoutPdfExporter.toZip(
				tenant.getName(),
				logoUrl,
				entry.sectionName(),
				entry.academicYear(),
				entry.levelName(),
				loginUrl,
				handout,
				entry.rows());
		String base = ProvisionFamilyHandoutPdfExporter.sanitizePathSegment(
				ProvisionFamilyHandoutPdfExporter.folderLabel(entry.sectionName()));
		return new ProvisionHandoutDownload(zip, base + ".zip");
	}

	private Guardian pickPrimaryGuardian(UUID studentId) {
		List<StudentGuardian> links = studentGuardianRepository.findActiveByStudentId(studentId);
		if (links == null || links.isEmpty()) {
			return null;
		}
		return links.stream()
				.sorted(Comparator
						.comparing(StudentGuardian::isPrimaryContact).reversed()
						.thenComparing(StudentGuardian::getCreatedAt,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.map(StudentGuardian::getGuardian)
				.filter(g -> g != null && !g.isDeleted())
				.findFirst()
				.orElse(null);
	}

	private ProvisionCredentialRow existingRow(String role, Student student, Guardian guardian) {
		UUID userId = guardian != null ? guardian.getUserId() : student.getUserId();
		User user = userId == null ? null : userRepository.findByPublicUuid(userId).orElse(null);
		String fullName;
		String documentType;
		String documentNumber;
		UUID studentUuid = student.getPublicUuid();
		UUID guardianUuid = guardian == null ? null : guardian.getPublicUuid();
		if (guardian != null) {
			fullName = guardian.fullName();
			documentType = guardian.getDocumentType() == null
					? null : guardian.getDocumentType().name();
			documentNumber = guardian.getDocumentNumber();
		}
		else {
			fullName = student.fullName();
			documentType = student.getDocumentType() == null
					? null : student.getDocumentType().name();
			documentNumber = student.getDocumentNumber();
		}
		EmailSource source = user == null ? null : user.getEmailSource();
		String email = user == null ? null : user.getEmail();
		return new ProvisionCredentialRow(
				role,
				fullName,
				documentType,
				documentNumber,
				email,
				null,
				source,
				"EXISTING",
				studentUuid,
				guardianUuid
		);
	}

	private CreatedAccount createPortalUser(
			String firstName,
			String lastName,
			String secondLastName,
			String rosterEmail,
			String documentNumber,
			UserRole role,
			String tenantSlug,
			boolean allowSynthetic
	) {
		EmailSource source;
		String email;
		if (SyntheticEmailFactory.looksLikeRealEmail(rosterEmail)
				&& !userRepository.existsByEmail(rosterEmail.trim().toLowerCase())) {
			email = rosterEmail.trim().toLowerCase();
			source = EmailSource.REAL;
		}
		else if (allowSynthetic) {
			email = SyntheticEmailFactory.generate(
					lastName,
					documentNumber,
					tenantSlug,
					userRepository::existsByEmail);
			source = EmailSource.SYNTHETIC;
		}
		else {
			throw new ConflictException("EMAIL_REQUIRED",
					"El padrón no tiene correo y no se permite generar sintético");
		}

		if (userRepository.existsByEmail(email)) {
			email = SyntheticEmailFactory.generate(
					lastName, documentNumber, tenantSlug, userRepository::existsByEmail);
			source = EmailSource.SYNTHETIC;
		}

		String temporaryPassword = TemporaryPasswordGenerator.generate();
		User user = new User();
		user.setFirstName(blankToPlaceholder(firstName));
		user.setLastName(blankToPlaceholder(lastName));
		user.setSecondLastName(secondLastName == null || secondLastName.isBlank()
				? null : secondLastName.trim());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(source == EmailSource.REAL);
		user.setEmailSource(source);
		user.setMustChangePassword(true);
		user.setMfaEnabled(false);
		user.addRole(role);
		User saved = userRepository.saveAndFlush(user);
		return new CreatedAccount(saved, temporaryPassword, source);
	}

	private static ProvisionCredentialRow toRow(
			CreatedAccount created,
			String role,
			Student student,
			Guardian guardian
	) {
		String fullName;
		String documentType;
		String documentNumber;
		UUID studentUuid = student == null ? null : student.getPublicUuid();
		UUID guardianUuid = guardian == null ? null : guardian.getPublicUuid();
		if (guardian != null) {
			fullName = guardian.fullName();
			documentType = guardian.getDocumentType() == null
					? null : guardian.getDocumentType().name();
			documentNumber = guardian.getDocumentNumber();
		}
		else {
			fullName = student.fullName();
			documentType = student.getDocumentType() == null
					? null : student.getDocumentType().name();
			documentNumber = student.getDocumentNumber();
		}
		return new ProvisionCredentialRow(
				role,
				fullName,
				documentType,
				documentNumber,
				created.user().getEmail(),
				created.temporaryPassword(),
				created.emailSource(),
				"CREATED",
				studentUuid,
				guardianUuid
		);
	}

	@SuppressWarnings("unchecked")
	private static String brandingLogoUrl(Map<String, Object> branding) {
		if (branding == null || branding.isEmpty()) {
			return null;
		}
		Object logoUrl = branding.get("logoUrl");
		if (logoUrl instanceof String s && !s.isBlank()) {
			return s.trim();
		}
		Object logo = branding.get("logo");
		if (logo instanceof Map<?, ?> logoMap) {
			Object light = logoMap.get("light");
			if (light instanceof String s && !s.isBlank()) {
				return s.trim();
			}
			Object url = logoMap.get("url");
			if (url instanceof String s && !s.isBlank()) {
				return s.trim();
			}
		}
		return null;
	}

	private static String buildSectionLabel(Section section) {
		String name = section.getName() == null ? "" : section.getName();
		try {
			if (section.getGrade() != null && section.getGrade().getName() != null) {
				return section.getGrade().getName() + " — " + name;
			}
		}
		catch (RuntimeException ignored) {
			// Lazy load may fail outside session; fall back to name.
		}
		return name;
	}

	private static String academicYearName(Section section) {
		try {
			if (section.getAcademicYear() != null && section.getAcademicYear().getName() != null) {
				String name = section.getAcademicYear().getName().trim();
				return name.isEmpty() ? null : name;
			}
		}
		catch (RuntimeException ignored) {
			// Lazy load may fail outside session.
		}
		return null;
	}

	private static String levelName(Section section) {
		try {
			if (section.getGrade() != null
					&& section.getGrade().getLevel() != null
					&& section.getGrade().getLevel().getName() != null) {
				String name = section.getGrade().getLevel().getName().trim();
				return name.isEmpty() ? null : name;
			}
		}
		catch (RuntimeException ignored) {
			// Lazy load may fail outside session.
		}
		return null;
	}

	private static String blankToPlaceholder(String value) {
		if (value == null || value.isBlank()) {
			return "Usuario";
		}
		return value.trim();
	}

	private record CreatedAccount(User user, String temporaryPassword, EmailSource emailSource) {}
}
