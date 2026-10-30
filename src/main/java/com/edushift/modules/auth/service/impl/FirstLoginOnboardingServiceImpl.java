package com.edushift.modules.auth.service.impl;

import com.edushift.modules.auth.dto.CompleteOnboardingRequest;
import com.edushift.modules.auth.dto.OnboardingMeResponse;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.entity.EmailSource;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.mapper.UserMapper;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.FirstLoginOnboardingService;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.provision.SyntheticEmailFactory;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.UnauthorizedException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirstLoginOnboardingServiceImpl implements FirstLoginOnboardingService {

	private final UserRepository userRepository;
	private final StudentRepository studentRepository;
	private final GuardianRepository guardianRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	@Override
	@Transactional(readOnly = true)
	public OnboardingMeResponse getOnboarding() {
		User user = requireCurrentUserEntity();
		String role = resolveRole(user);
		Student student = null;
		Guardian guardian = null;
		if ("STUDENT".equals(role)) {
			student = studentRepository.findByUserId(user.getPublicUuid()).orElse(null);
		}
		else if ("PARENT".equals(role)) {
			guardian = guardianRepository.findByUserId(user.getPublicUuid()).orElse(null);
		}

		String phone = coalesce(user.getPhone(),
				student != null ? student.getPhone() : null,
				guardian != null ? guardian.getPhone() : null);
		String firstName = coalesce(user.getFirstName(),
				student != null ? student.getFirstName() : null,
				guardian != null ? guardian.getFirstName() : null);
		String lastName = coalesce(user.getLastName(),
				student != null ? student.getLastName() : null,
				guardian != null ? guardian.getLastName() : null);

		List<String> required = new ArrayList<>();
		List<String> optional = new ArrayList<>();
		required.add("newPassword");
		if (isBlankOrPlaceholder(firstName)) {
			required.add("firstName");
		}
		if (isBlankOrPlaceholder(lastName)) {
			required.add("lastName");
		}
		if (isBlank(phone)) {
			required.add("phone");
		}
		optional.add("email");
		if ("STUDENT".equals(role)) {
			optional.add("birthDate");
			optional.add("address");
		}

		boolean synthetic = user.getEmailSource() == EmailSource.SYNTHETIC
				|| SyntheticEmailFactory.isSyntheticAddress(user.getEmail());

		return new OnboardingMeResponse(
				role,
				user.isMustChangePassword(),
				user.getEmailSource(),
				synthetic,
				firstName,
				lastName,
				phone,
				user.getEmail(),
				student != null ? student.getBirthDate() : null,
				student != null ? student.getAddress() : null,
				List.copyOf(required),
				List.copyOf(optional)
		);
	}

	@Override
	@Transactional
	public UserResponse completeOnboarding(CompleteOnboardingRequest request) {
		User user = requireCurrentUserEntity();
		String role = resolveRole(user);

		if (!user.isMustChangePassword()) {
			throw new UnauthorizedException("PASSWORD_CHANGE_NOT_REQUIRED",
					"This account cannot use the initial password change flow");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new BadRequestException("PASSWORD_UNCHANGED",
					"New password must be different from the current one");
		}

		String firstName = trimToNull(request.firstName());
		String lastName = trimToNull(request.lastName());
		String phone = trimToNull(request.phone());

		if (firstName == null && isBlankOrPlaceholder(user.getFirstName())) {
			throw new BadRequestException("FIRST_NAME_REQUIRED", "El nombre es obligatorio");
		}
		if (lastName == null && isBlankOrPlaceholder(user.getLastName())) {
			throw new BadRequestException("LAST_NAME_REQUIRED", "El apellido es obligatorio");
		}
		if (phone == null && isBlank(user.getPhone())) {
			Student st = "STUDENT".equals(role)
					? studentRepository.findByUserId(user.getPublicUuid()).orElse(null) : null;
			Guardian gu = "PARENT".equals(role)
					? guardianRepository.findByUserId(user.getPublicUuid()).orElse(null) : null;
			String existingPhone = coalesce(user.getPhone(),
					st != null ? st.getPhone() : null,
					gu != null ? gu.getPhone() : null);
			if (isBlank(existingPhone)) {
				throw new BadRequestException("PHONE_REQUIRED", "El teléfono es obligatorio");
			}
		}

		if (firstName != null) {
			user.setFirstName(firstName);
		}
		if (lastName != null) {
			user.setLastName(lastName);
		}
		if (phone != null) {
			user.setPhone(phone);
		}

		if (request.email() != null && !request.email().isBlank()) {
			String nextEmail = request.email().trim().toLowerCase();
			boolean sameAsCurrent = nextEmail.equalsIgnoreCase(user.getEmail());
			if (!sameAsCurrent) {
				if (SyntheticEmailFactory.isSyntheticAddress(nextEmail)) {
					throw new BadRequestException("EMAIL_SYNTHETIC_NOT_ALLOWED",
							"Provide a real email address");
				}
				if (userRepository.existsByEmail(nextEmail)) {
					throw new ConflictException("EMAIL_ALREADY_EXISTS",
							"That email is already in use");
				}
				user.setEmail(nextEmail);
				user.setEmailSource(EmailSource.REAL);
				user.setEmailVerified(false);
			}
			// If unchanged and still synthetic: keep SYNTHETIC (do not flip to REAL).
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.setMustChangePassword(false);
		User saved = userRepository.saveAndFlush(user);

		if ("STUDENT".equals(role)) {
			studentRepository.findByUserId(saved.getPublicUuid()).ifPresent(student -> {
				if (firstName != null) {
					student.setFirstName(firstName);
				}
				if (lastName != null) {
					student.setLastName(lastName);
				}
				if (phone != null) {
					student.setPhone(phone);
				}
				if (request.email() != null && !request.email().isBlank()
						&& !SyntheticEmailFactory.isSyntheticAddress(
						request.email().trim().toLowerCase())) {
					student.setEmail(request.email().trim().toLowerCase());
				}
				if (request.birthDate() != null) {
					student.setBirthDate(request.birthDate());
				}
				if (request.address() != null) {
					String address = request.address().trim();
					student.setAddress(address.isEmpty() ? null : address);
				}
				studentRepository.save(student);
			});
		}
		else if ("PARENT".equals(role)) {
			guardianRepository.findByUserId(saved.getPublicUuid()).ifPresent(guardian -> {
				if (firstName != null) {
					guardian.setFirstName(firstName);
				}
				if (lastName != null) {
					guardian.setLastName(lastName);
				}
				if (phone != null) {
					guardian.setPhone(phone);
				}
				if (request.email() != null && !request.email().isBlank()
						&& !SyntheticEmailFactory.isSyntheticAddress(
						request.email().trim().toLowerCase())) {
					guardian.setEmail(request.email().trim().toLowerCase());
				}
				guardianRepository.save(guardian);
			});
		}

		log.info("[auth] onboarding completed -- publicUuid={} role={}",
				saved.getPublicUuid(), role);
		return userMapper.toResponse(saved);
	}

	private static String resolveRole(User user) {
		if (user.hasRole(UserRole.STUDENT)) {
			return "STUDENT";
		}
		if (user.hasRole(UserRole.PARENT)) {
			return "PARENT";
		}
		return "OTHER";
	}

	private User requireCurrentUserEntity() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
			throw new UnauthorizedException("Authentication required");
		}
		UUID publicUuid;
		try {
			publicUuid = UUID.fromString(auth.getName());
		}
		catch (IllegalArgumentException e) {
			throw new UnauthorizedException("INVALID_PRINCIPAL",
					"Authenticated principal is not a valid UUID");
		}
		return userRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
						"Authenticated user no longer exists"));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean isBlankOrPlaceholder(String value) {
		return isBlank(value) || "Usuario".equalsIgnoreCase(value.trim());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}

	private static String coalesce(String... values) {
		if (values == null) {
			return null;
		}
		for (String v : values) {
			if (v != null && !v.isBlank()) {
				return v.trim();
			}
		}
		return null;
	}
}
