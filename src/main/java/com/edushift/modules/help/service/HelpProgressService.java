package com.edushift.modules.help.service;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.help.dto.HelpFeedbackResponse;
import com.edushift.modules.help.dto.HelpProgressItem;
import com.edushift.modules.help.entity.HelpFeedback;
import com.edushift.modules.help.entity.HelpProgress;
import com.edushift.modules.help.repository.HelpFeedbackRepository;
import com.edushift.modules.help.repository.HelpProgressRepository;
import com.edushift.shared.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists per-user checklist progress and per-user feedback for the
 * help manuals.
 *
 * <h3>Tenant isolation</h3>
 * Tenant and user are taken from the authenticated session via
 * {@code CurrentUserProvider}. They are never accepted from the request
 * body — accepting them would let an attacker escalate scope across
 * tenants or impersonate other users.
 *
 * <h3>Idempotency</h3>
 * {@link #setProgress} upserts the row keyed by
 * (tenant, user, role, chapterFile, itemId). Re-submitting the same
 * {@code checked} value is a no-op; re-submitting a different value
 * updates the existing row.
 */
@Service
public class HelpProgressService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "TENANT_ADMIN", "TEACHER", "STUDENT", "PARENT", "STAFF"
    );

    private final HelpProgressRepository progressRepo;
    private final HelpFeedbackRepository feedbackRepo;
    private final UserRepository userRepository;

    public HelpProgressService(
            HelpProgressRepository progressRepo,
            HelpFeedbackRepository feedbackRepo,
            UserRepository userRepository) {
        this.progressRepo = progressRepo;
        this.feedbackRepo = feedbackRepo;
        this.userRepository = userRepository;
    }

    /**
     * Resolve the current user's public UUID into an internal {@link User}
     * so we can persist the FK to {@code users.id}. Returns null if no
     * authenticated session is bound.
     */
    public UUID resolveInternalUserId(UUID publicUuid) {
        if (publicUuid == null) {
            return null;
        }
        return userRepository.findByPublicUuid(publicUuid)
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Authenticated user no longer exists."));
    }

    @Transactional(readOnly = true)
    public List<HelpProgressItem> getProgress(
            UUID tenantId, UUID userId, String role, String chapterFile) {
        validateRole(role);
        return progressRepo.findByUserChapter(tenantId, userId, role.toUpperCase(Locale.ROOT), chapterFile)
                .stream()
                .map(p -> new HelpProgressItem(p.getItemId(), p.isChecked(), p.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public HelpProgressItem setProgress(
            UUID tenantId, UUID userId, String role, String chapterFile,
            String itemId, boolean checked) {
        validateRole(role);
        String normalisedRole = role.toUpperCase(Locale.ROOT);

        HelpProgress row = progressRepo
                .findOne(tenantId, userId, normalisedRole, chapterFile, itemId)
                .orElseGet(() -> {
                    HelpProgress fresh = new HelpProgress();
                    fresh.setTenantId(tenantId);
                    fresh.setUserId(userId);
                    fresh.setRole(normalisedRole);
                    fresh.setChapterFile(chapterFile);
                    fresh.setItemId(itemId);
                    return fresh;
                });

        row.setChecked(checked);
        HelpProgress saved = progressRepo.save(row);
        return new HelpProgressItem(saved.getItemId(), saved.isChecked(), saved.getUpdatedAt());
    }

    @Transactional
    public HelpProgressItem clearProgress(
            UUID tenantId, UUID userId, String role, String chapterFile, String itemId) {
        validateRole(role);
        progressRepo.softDelete(tenantId, userId, role.toUpperCase(Locale.ROOT), chapterFile, itemId);
        return new HelpProgressItem(itemId, false, Instant.now());
    }

    @Transactional
    public HelpFeedbackResponse postFeedback(
            UUID tenantId, UUID userId, String role, String chapterFile, String body) {
        validateRole(role);
        String normalisedRole = role.toUpperCase(Locale.ROOT);

        HelpFeedback f = new HelpFeedback();
        f.setTenantId(tenantId);
        f.setUserId(userId);
        f.setRole(normalisedRole);
        f.setChapterFile(chapterFile);
        f.setBody(body);
        f.setStatus(HelpFeedback.FeedbackStatus.OPEN);

        return HelpFeedbackResponse.from(feedbackRepo.save(f));
    }

    @Transactional(readOnly = true)
    public List<HelpFeedbackResponse> listMyFeedback(UUID tenantId, UUID userId, String role) {
        validateRole(role);
        return feedbackRepo.findByUserRole(tenantId, userId, role.toUpperCase(Locale.ROOT))
                .stream()
                .map(HelpFeedbackResponse::from)
                .toList();
    }

    private void validateRole(String role) {
        if (role == null || !ALLOWED_ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Invalid role: " + role + ". Allowed: " + ALLOWED_ROLES);
        }
    }
}