package com.edushift.modules.disclosures.security;

import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.disclosures.mapper.AnnualDisclosureMapper;
import com.edushift.modules.disclosures.repository.AnnualDisclosurePackageRepository;
import com.edushift.modules.disclosures.repository.AnnualDisclosurePublicationRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileReadAccessGuard;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Adds family-link and reference checks to metadata reads, downloads and deletion of disclosure files. */
@Component
@RequiredArgsConstructor
public class DisclosureFileAccessGuard implements FileReadAccessGuard {
	private final CurrentUserProvider currentUserProvider;
	private final StudentGuardianRepository studentGuardianRepository;
	private final AnnualDisclosurePackageRepository packageRepository;
	private final AnnualDisclosurePublicationRepository publicationRepository;
	private final AnnualDisclosureMapper mapper;
	private final AuditLogger auditLogger;

	@Override
	public void requireReadable(FileObject file, Authentication authentication) {
		if (!isDisclosureFile(file)) return;
		if (hasAuthority(authentication, LmsAuthorities.LMS_DISCLOSURE_READ)
				|| hasAuthority(authentication, LmsAuthorities.LMS_DISCLOSURE_MANAGE)) {
			auditLogger.log(AuditAction.READ, "AnnualDisclosureAttachment", file.getPublicUuid(),
					"Acceso administrativo a un anexo del expediente", java.util.Map.of("fileType", "disclosure"), "disclosures");
			return;
		}
		UUID userId = currentUserProvider.currentUserId().orElse(null);
		if (!hasRole(authentication, "PARENT") || userId == null
				|| studentGuardianRepository.findActiveByGuardianUserId(userId).isEmpty()
				|| publicationRepository.findAllByOrderByPublishedAtDesc().stream()
						.noneMatch(item -> mapper.referencesFamilyVisibleFile(item.getContent(), file.getPublicUuid()))) {
			throw new ForbiddenException("DISCLOSURE_ATTACHMENT_ACCESS_DENIED", "No tienes acceso a este anexo publicado.");
		}
		auditLogger.log(AuditAction.READ, "AnnualDisclosureAttachment", file.getPublicUuid(),
				"Acceso familiar a un anexo publicado", java.util.Map.of("fileType", "disclosure"), "disclosures");
	}

	@Override
	public void requireDeletable(FileObject file, Authentication authentication) {
		if (!isDisclosureFile(file)) return;
		if (hasRole(authentication, "TENANT_ADMIN")
				|| !hasAuthority(authentication, LmsAuthorities.LMS_DISCLOSURE_MANAGE)) {
			throw new ForbiddenException("DISCLOSURE_ATTACHMENT_DELETE_DENIED", "Solo el personal autorizado que prepara el expediente puede retirar un anexo sin referencias.");
		}
		boolean referencedByDraft = packageRepository.findAll().stream()
				.anyMatch(pkg -> mapper.referencesFile(pkg.getContent(), file.getPublicUuid()));
		boolean referencedByPublication = publicationRepository.findAll().stream()
				.anyMatch(item -> mapper.referencesFile(item.getContent(), file.getPublicUuid()));
		if (referencedByDraft || referencedByPublication) {
			throw new ConflictException("DISCLOSURE_ATTACHMENT_IN_USE", "Retira el anexo del expediente antes de eliminar el archivo.");
		}
	}

	private static boolean isDisclosureFile(FileObject file) {
		return file.getRemoteKey() != null && file.getRemoteKey().contains("/disclosures/");
	}

	private static boolean hasRole(Authentication authentication, String role) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
	}

	private static boolean hasAuthority(Authentication authentication, String expected) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals(expected));
	}
}
