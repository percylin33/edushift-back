package com.edushift.modules.ai.rag;

import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.entity.FileUploadStatus;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiSourceService {

    private static final int MAX_FILE_BYTES = 25_000_000;
    private final AiSourceDocumentRepository documents;
    private final AiEntitlementService entitlements;
    private final FileObjectService files;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<AiSourceResponse> list() {
        entitlements.require(AiEntitlementService.RAG_CORE);
        return documents.findAllByOrderByUpdatedAtDesc().stream()
                .map(AiSourceResponse::from).toList();
    }

    @Transactional
    public AiSourceResponse register(AiSourceRequest request) {
        var plan = entitlements.require(AiEntitlementService.RAG_CORE);
        if (request.kind() == AiSourceDocument.Kind.INSTITUTIONAL) {
            entitlements.require(AiEntitlementService.RAG_SCHOOL_DOCS);
        }
        UUID tenantId = TenantContext.currentRequired();
        validateProvenance(request);
        jdbc.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) q",
                Integer.class, tenantId.toString());
        if (documents.existsByFilePublicUuid(request.filePublicUuid())) {
            throw new ConflictException("AI_SOURCE_ALREADY_REGISTERED",
                    "Este archivo ya está registrado como fuente.");
        }
        if (documents.countExceptStatus(AiSourceDocument.Status.RETIRED)
                >= plan.institutionalDocumentLimit()) {
            throw new ConflictException("AI_SOURCE_LIMIT", "Se alcanzó el límite de documentos del plan.");
        }
        FileObject file = files.findByPublicUuid(request.filePublicUuid())
                .orElseThrow(() -> notFound());
        if (!file.getRemoteKey().contains("/ai-sources/")) {
            throw notFound();
        }
        if (file.getStatus() != FileUploadStatus.READY) {
            throw new ConflictException("AI_SOURCE_NOT_READY", "El archivo aún no está listo.");
        }
        if (file.getSizeBytes() > MAX_FILE_BYTES) {
            throw new BadRequestException("AI_SOURCE_TOO_LARGE",
                    "El archivo supera el máximo de 25 MB.");
        }
        AiSourceDocument document = new AiSourceDocument();
        document.setTenantId(tenantId);
        document.setFilePublicUuid(file.getPublicUuid());
        document.setTitle(request.title().trim());
        document.setKind(request.kind());
        document.setAudiences(request.audiences().stream().distinct().map(Enum::name).toList());
        document.setSourceUrl(request.kind() == AiSourceDocument.Kind.OFFICIAL
                ? request.sourceUrl().trim() : null);
        document.setSourceVersion(request.sourceVersion());
        document.setEffectiveAt(request.effectiveAt());
        document.setReviewAfter(request.reviewAfter());
        document.setChecksumSha256(file.getChecksumSha256());
        document.setTextBytes(0);
        document.setStatus(AiSourceDocument.Status.INDEXING);
        document = documents.saveAndFlush(document);
        files.acquireReference(file.getPublicUuid());
        return AiSourceResponse.from(document);
    }

    @Transactional
    public AiSourceResponse publish(UUID publicUuid) {
        entitlements.require(AiEntitlementService.RAG_CORE);
        AiSourceDocument doc = documents.findByPublicUuidForUpdate(publicUuid)
                .orElseThrow(AiSourceService::notFound);
        if (doc.getStatus() != AiSourceDocument.Status.READY) {
            throw new ConflictException("AI_SOURCE_NOT_READY", "La fuente no tiene texto listo.");
        }
        if (doc.getReviewAfter() != null
                && doc.getReviewAfter().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new ConflictException("AI_SOURCE_REVIEW_EXPIRED",
                    "La fecha de revisión de la fuente ya venció.");
        }
        doc.setStatus(AiSourceDocument.Status.PUBLISHED);
        return AiSourceResponse.from(documents.save(doc));
    }

    @Transactional
    public AiSourceResponse retry(UUID publicUuid) {
        entitlements.require(AiEntitlementService.RAG_CORE);
        AiSourceDocument doc = documents.findByPublicUuidForUpdate(publicUuid)
                .orElseThrow(AiSourceService::notFound);
        if (doc.getStatus() != AiSourceDocument.Status.FAILED
                && doc.getStatus() != AiSourceDocument.Status.NEEDS_OCR) {
            throw new ConflictException("AI_SOURCE_NOT_RETRYABLE",
                    "Solo se puede reintentar una fuente fallida o sin texto.");
        }
        doc.setStatus(AiSourceDocument.Status.INDEXING);
        doc.setIndexErrorCode(null);
        return AiSourceResponse.from(documents.save(doc));
    }

    @Transactional
    public void retire(UUID publicUuid) {
        entitlements.require(AiEntitlementService.RAG_CORE);
        AiSourceDocument doc = documents.findByPublicUuidForUpdate(publicUuid)
                .orElseThrow(AiSourceService::notFound);
        if (doc.getStatus() == AiSourceDocument.Status.RETIRED) return;
        doc.setStatus(AiSourceDocument.Status.RETIRED);
        documents.save(doc);
        files.releaseReference(doc.getFilePublicUuid());
    }

    private static void validateProvenance(AiSourceRequest request) {
        if (request.kind() != AiSourceDocument.Kind.OFFICIAL) return;
        if (request.sourceUrl() == null || request.sourceVersion() == null
                || request.sourceVersion().isBlank() || request.reviewAfter() == null) {
            throw new BadRequestException("AI_SOURCE_PROVENANCE_REQUIRED",
                    "Una fuente oficial necesita URL, versión y fecha de revisión.");
        }
        try {
            URI uri = URI.create(request.sourceUrl());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean official = host.equals("gob.pe") || host.endsWith(".gob.pe")
                    || host.equals("elperuano.pe") || host.endsWith(".elperuano.pe");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !official
                    || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("AI_SOURCE_OFFICIAL_URL_INVALID",
                    "La URL de la fuente debe ser de un dominio oficial peruano HTTPS.");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("AI_SOURCE_NOT_FOUND", "Fuente no encontrada.");
    }
}
