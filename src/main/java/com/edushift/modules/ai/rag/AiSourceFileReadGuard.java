package com.edushift.modules.ai.rag;

import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.modules.ai.exception.AiPlanUnavailableException;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileReadAccessGuard;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.LmsAuthorities;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Enforces RAG source audience and publication on the generic Files endpoints. */
@Component
@RequiredArgsConstructor
public class AiSourceFileReadGuard implements FileReadAccessGuard {

    private final AiSourceDocumentRepository documents;
    private final AiEntitlementService entitlements;

    @Override
    public void requireReadable(FileObject file, Authentication authentication) {
        if (!file.getRemoteKey().contains("/ai-sources/")) return;
        if (has(authentication, LmsAuthorities.AI_SOURCE_MANAGE)) return;
        AiSourceDocument source = documents.findByFilePublicUuid(file.getPublicUuid())
                .orElseThrow(AiSourceFileReadGuard::notFound);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (source.getStatus() != AiSourceDocument.Status.PUBLISHED
                || source.getEffectiveAt() != null && source.getEffectiveAt().isAfter(today)
                || source.getReviewAfter() != null && source.getReviewAfter().isBefore(today)) {
            throw notFound();
        }
        AiEntitlementService.EffectiveAiPlan plan;
        try {
            plan = entitlements.current();
        } catch (AiPlanUnavailableException ex) {
            throw notFound();
        }
        if (!plan.features().contains(AiEntitlementService.RAG_CORE)
                || source.getKind() == AiSourceDocument.Kind.INSTITUTIONAL
                && !plan.features().contains(AiEntitlementService.RAG_SCHOOL_DOCS)) {
            throw notFound();
        }
        boolean allowed = (source.getAudiences().contains(AiSourceDocument.Audience.STUDENT.name())
                && has(authentication, LmsAuthorities.AI_TUTOR_USE)
                && plan.features().contains(AiEntitlementService.TUTOR))
                || (source.getAudiences().contains(AiSourceDocument.Audience.TEACHER.name())
                && has(authentication, LmsAuthorities.AI_TEACHER_USE)
                && plan.features().contains(AiEntitlementService.TEACHER))
                || (source.getAudiences().contains(AiSourceDocument.Audience.FAMILY.name())
                && has(authentication, LmsAuthorities.AI_FAMILY_USE)
                && plan.features().contains(AiEntitlementService.FAMILY));
        if (!allowed) throw notFound();
    }

    private static boolean has(Authentication auth, String authority) {
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("AI_SOURCE_NOT_FOUND", "Fuente no encontrada.");
    }
}
