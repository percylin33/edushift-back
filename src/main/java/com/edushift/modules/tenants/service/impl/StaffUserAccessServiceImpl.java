package com.edushift.modules.tenants.service.impl;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.tenants.dto.ReplaceStaffAccessRequest;
import com.edushift.modules.tenants.dto.StaffAccessState;
import com.edushift.modules.tenants.entity.StaffUserPermissionOverride;
import com.edushift.modules.tenants.entity.StaffUserProfileAssignment;
import com.edushift.modules.tenants.repository.StaffUserPermissionOverrideRepository;
import com.edushift.modules.tenants.repository.StaffUserProfileAssignmentRepository;
import com.edushift.modules.tenants.service.StaffUserAccessService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.security.StaffAccessCatalog;
import com.edushift.shared.security.StaffAccessCatalog.ProfileCode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffUserAccessServiceImpl implements StaffUserAccessService {

	private final UserRepository userRepository;
	private final StaffUserProfileAssignmentRepository profileRepository;
	private final StaffUserPermissionOverrideRepository overrideRepository;
	private final LmsRoleAuthorityMapper authorityMapper;
	private final AuditLogger auditLogger;

	@Override
	@Transactional(readOnly = true)
	public StaffAccessState getForUser(UUID tenantId, UUID publicUserUuid) {
		User user = requireStaffUser(tenantId, publicUserUuid);
		return stateFor(tenantId, user);
	}

	@Override
	@Transactional
	public StaffAccessState replaceForUser(
			UUID tenantId,
			UUID publicUserUuid,
			ReplaceStaffAccessRequest request) {
		User user = requireStaffUser(tenantId, publicUserUuid);
		if (request.profiles().stream().anyMatch(java.util.Objects::isNull)) {
			throw new ConflictException("INVALID_STAFF_PROFILE",
					"Each selected staff profile must be valid");
		}
		Set<ProfileCode> requestedProfiles = EnumSet.noneOf(ProfileCode.class);
		requestedProfiles.addAll(request.profiles());
		Set<String> allowedAuthorities = StaffAccessCatalog.permissions().stream()
				.map(StaffAccessCatalog.PermissionOption::authority)
				.collect(Collectors.toSet());
		if (request.overrides().keySet().stream().anyMatch(authority -> !allowedAuthorities.contains(authority))) {
			throw new ConflictException("INVALID_STAFF_PERMISSION",
					"One or more staff permissions are not supported");
		}
		if (request.overrides().values().stream().anyMatch(java.util.Objects::isNull)) {
			throw new ConflictException("INVALID_STAFF_PERMISSION",
					"Each individual permission override must be granted or revoked");
		}

		Set<String> nextCodes = requestedProfiles.stream()
				.map(Enum::name).collect(Collectors.toSet());
		List<StaffUserProfileAssignment> currentProfiles =
				profileRepository.findByTenantIdAndUserIdAndDeletedFalse(tenantId, user.getId());
		Set<String> currentCodes = currentProfiles.stream()
				.map(StaffUserProfileAssignment::getProfileCode).collect(Collectors.toSet());
		Set<String> previousCodes = Set.copyOf(currentCodes);
		List<StaffUserProfileAssignment> profilesToRemove = currentProfiles.stream()
				.filter(assignment -> !nextCodes.contains(assignment.getProfileCode()))
				.toList();
		profileRepository.deleteAll(profilesToRemove);
		profileRepository.flush();
		for (ProfileCode code : requestedProfiles) {
			if (currentCodes.contains(code.name())) continue;
			StaffUserProfileAssignment assignment = new StaffUserProfileAssignment();
			assignment.setTenantId(tenantId);
			assignment.setUserId(user.getId());
			assignment.setProfileCode(code.name());
			profileRepository.save(assignment);
		}

		Map<String, StaffUserPermissionOverride> currentOverrides = overrideRepository
				.findByTenantIdAndUserIdAndDeletedFalse(tenantId, user.getId()).stream()
				.collect(Collectors.toMap(StaffUserPermissionOverride::getAuthority,
						Function.identity(), (left, right) -> left, LinkedHashMap::new));
		Map<String, Boolean> previousOverrides = currentOverrides.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						entry -> entry.getValue().isGranted(), (left, right) -> left,
						LinkedHashMap::new));
		for (Map.Entry<String, StaffUserPermissionOverride> entry : currentOverrides.entrySet()) {
			if (!request.overrides().containsKey(entry.getKey())) {
				entry.getValue().setDeleted(true);
				entry.getValue().setDeletedAt(Instant.now());
				overrideRepository.save(entry.getValue());
			}
		}
		request.overrides().forEach((authority, granted) -> {
			StaffUserPermissionOverride row = currentOverrides.get(authority);
			if (row == null || row.isDeleted()) {
				row = new StaffUserPermissionOverride();
				row.setTenantId(tenantId);
				row.setUserId(user.getId());
				row.setAuthority(authority);
			}
			row.setGranted(granted);
			row.setDeleted(false);
			row.setDeletedAt(null);
			overrideRepository.save(row);
		});
		auditLogger.log(AuditAction.CONFIG_CHANGED, "STAFF_ACCESS", user.getPublicUuid(),
				"Actualizó perfiles y permisos de un usuario del personal",
				Map.of("beforeProfiles", previousCodes, "afterProfiles", nextCodes,
						"beforeOverrides", previousOverrides, "afterOverrides", request.overrides()),
				"users");
		return stateFor(tenantId, user);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<String> profileAuthoritiesFor(UUID tenantId, UUID internalUserId) {
		Set<String> authorities = new LinkedHashSet<>();
		for (StaffUserProfileAssignment assignment :
				profileRepository.findByTenantIdAndUserIdAndDeletedFalse(tenantId, internalUserId)) {
			try {
				ProfileCode code = ProfileCode.valueOf(assignment.getProfileCode());
				authorities.addAll(StaffAccessCatalog.authoritiesFor(code));
			} catch (IllegalArgumentException ignored) {
				// Unknown profile codes fail closed and can be migrated separately.
			}
		}
		return Set.copyOf(authorities);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Boolean> overridesFor(UUID tenantId, UUID internalUserId) {
		Map<String, Boolean> overrides = new LinkedHashMap<>();
		for (StaffUserPermissionOverride override :
				overrideRepository.findByTenantIdAndUserIdAndDeletedFalse(tenantId, internalUserId)) {
			overrides.put(override.getAuthority(), override.isGranted());
		}
		return Map.copyOf(overrides);
	}

	private StaffAccessState stateFor(UUID tenantId, User user) {
		Set<ProfileCode> profiles = profileRepository
				.findByTenantIdAndUserIdAndDeletedFalse(tenantId, user.getId()).stream()
				.map(StaffUserProfileAssignment::getProfileCode)
				.map(ProfileCode::valueOf)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(ProfileCode.class)));
		Map<String, Boolean> overrides = overridesFor(tenantId, user.getId());
		Set<String> base = authorityMapper.resolveFor(tenantId, user.getRoleSet());
		Set<String> effective = authorityMapper.resolveFor(tenantId, user.getId(), user.getRoleSet());
		return new StaffAccessState(profiles, overrides, base, effective,
				StaffAccessCatalog.profiles(), StaffAccessCatalog.permissions());
	}

	private User requireStaffUser(UUID tenantId, UUID publicUserUuid) {
		User user = userRepository.findByPublicUuid(publicUserUuid)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		if (!tenantId.equals(user.getTenantId())) {
			throw new ResourceNotFoundException("User not found");
		}
		if (!user.hasRole(UserRole.STAFF)) {
			throw new ConflictException("USER_NOT_STAFF",
					"Staff access profiles can only be assigned to STAFF users");
		}
		return user;
	}
}
