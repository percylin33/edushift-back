package com.edushift.modules.admin.invitations;

import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GoneException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SchoolInvitationServiceImpl implements SchoolInvitationService {

	static final Duration TOKEN_TTL = Duration.ofDays(7);

	private final SchoolInvitationRepository repository;
	private final SchoolInvitationMailer mailer;
	private final SecureRandom secureRandom = new SecureRandom();

	public SchoolInvitationServiceImpl(
			SchoolInvitationRepository repository,
			SchoolInvitationMailer mailer
	) {
		this.repository = repository;
		this.mailer = mailer;
	}

	@Override
	@Transactional
	public SchoolInvitationResponse create(CreateSchoolInvitationRequest request) {
		Instant now = Instant.now();
		String email = request.email().trim().toLowerCase();

		repository.findActivePendingByEmail(email, now).ifPresent(existing -> {
			throw new ConflictException("SCHOOL_INVITATION_ALREADY_PENDING",
					"Ya existe una invitación pendiente para " + email);
		});

		SchoolInvitation invitation = new SchoolInvitation();
		invitation.setEmail(email);
		invitation.setToken(generateToken());
		invitation.setExpiresAt(now.plus(TOKEN_TTL));
		SchoolInvitation saved = repository.save(invitation);

		String setupUrl = mailer.setupUrl(saved.getToken());
		boolean sent = mailer.send(email, setupUrl, saved.getExpiresAt());
		log.info("[school-invites] created publicUuid={} email='{}' emailSent={}",
				saved.getPublicUuid(), email, sent);
		return toResponse(saved, now, saved.getToken(), setupUrl, sent);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SchoolInvitationResponse> listPending(Pageable pageable) {
		Instant now = Instant.now();
		return repository.findPending(now, pageable)
				.map(inv -> toResponse(inv, now, null, null, false));
	}

	@Override
	@Transactional
	public SchoolInvitationResponse cancel(UUID publicUuid) {
		Instant now = Instant.now();
		SchoolInvitation invitation = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("SchoolInvitation", publicUuid));
		if (invitation.isAccepted()) {
			throw new ConflictException("SCHOOL_INVITATION_ALREADY_ACCEPTED",
					"La invitación ya fue aceptada; no se puede cancelar");
		}
		if (!invitation.isCancelled()) {
			invitation.markCancelled(now);
			invitation = repository.save(invitation);
			log.info("[school-invites] cancelled publicUuid={}", invitation.getPublicUuid());
		}
		return toResponse(invitation, now, null, null, false);
	}

	@Override
	@Transactional
	public SchoolInvitationResponse resend(UUID publicUuid) {
		Instant now = Instant.now();
		SchoolInvitation invitation = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("SchoolInvitation", publicUuid));
		if (!invitation.isPending(now)) {
			throw new ConflictException("SCHOOL_INVITATION_NOT_PENDING",
					"Solo se puede reenviar una invitación pendiente");
		}
		invitation.setToken(generateToken());
		invitation.setExpiresAt(now.plus(TOKEN_TTL));
		SchoolInvitation saved = repository.save(invitation);
		String setupUrl = mailer.setupUrl(saved.getToken());
		boolean sent = mailer.send(saved.getEmail(), setupUrl, saved.getExpiresAt());
		log.info("[school-invites] resent publicUuid={} emailSent={}", saved.getPublicUuid(), sent);
		return toResponse(saved, now, saved.getToken(), setupUrl, sent);
	}

	@Override
	@Transactional(readOnly = true)
	public SchoolInvitationPreflight getPreflight(String token) {
		SchoolInvitation invitation = requirePending(token);
		return new SchoolInvitationPreflight(invitation.getEmail());
	}

	@Override
	@Transactional(readOnly = true)
	public SchoolInvitation requirePending(String token) {
		Instant now = Instant.now();
		if (token == null || token.isBlank()) {
			throw new ResourceNotFoundException("SchoolInvitation", "<by token>");
		}
		SchoolInvitation invitation = repository.findByToken(token.trim())
				.orElseThrow(() -> new ResourceNotFoundException("SchoolInvitation", "<by token>"));
		if (invitation.isAccepted()) {
			throw new GoneException("SCHOOL_INVITATION_ALREADY_ACCEPTED",
					"Esta invitación ya fue utilizada");
		}
		if (invitation.isCancelled()) {
			throw new GoneException("SCHOOL_INVITATION_CANCELLED",
					"Esta invitación fue cancelada");
		}
		if (invitation.isExpired(now)) {
			throw new GoneException("SCHOOL_INVITATION_EXPIRED",
					"Esta invitación venció; pide una nueva al equipo de EduShift");
		}
		return invitation;
	}

	@Override
	@Transactional
	public void markAccepted(UUID invitationId, UUID createdTenantId) {
		SchoolInvitation tracked = repository.findById(invitationId)
				.orElseThrow(() -> new ResourceNotFoundException("SchoolInvitation", invitationId));
		tracked.markAccepted(Instant.now(), createdTenantId);
		repository.save(tracked);
	}

	private SchoolInvitationResponse toResponse(
			SchoolInvitation invitation,
			Instant now,
			String token,
			String setupUrl,
			boolean emailSent
	) {
		InvitationStatus status;
		if (invitation.isAccepted()) {
			status = InvitationStatus.ACCEPTED;
		} else if (invitation.isCancelled()) {
			status = InvitationStatus.CANCELLED;
		} else if (invitation.isExpired(now)) {
			status = InvitationStatus.EXPIRED;
		} else {
			status = InvitationStatus.PENDING;
		}
		return new SchoolInvitationResponse(
				invitation.getPublicUuid(),
				invitation.getEmail(),
				status,
				token,
				setupUrl,
				emailSent,
				invitation.getExpiresAt(),
				invitation.getAcceptedAt(),
				invitation.getCancelledAt(),
				invitation.getCreatedAt()
		);
	}

	private String generateToken() {
		byte[] raw = new byte[24];
		secureRandom.nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}
}
