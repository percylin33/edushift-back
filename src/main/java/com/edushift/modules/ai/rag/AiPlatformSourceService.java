package com.edushift.modules.ai.rag;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.files.config.StorageEnvResolver;
import com.edushift.modules.files.storage.StoragePutRequest;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.modules.files.storage.StoredObject;
import com.edushift.modules.files.validator.FileValidator;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.identifier.UuidV7;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Platform-owned national sources. Mutations are exposed only from SUPER_ADMIN endpoints. */
@Service
@RequiredArgsConstructor
public class AiPlatformSourceService {

    private static final long MAX_FILE_BYTES = 25_000_000L;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final StorageEnvResolver storageEnvResolver;
    private final FileValidator fileValidator;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogger auditLogger;

    @Transactional(readOnly = true)
    public List<AiPlatformSourceResponse> list() {
        return jdbc.query("""
                SELECT public_uuid, title, status, audiences::text AS audiences,
                       source_url, source_version, effective_at, review_after,
                       original_name, size_bytes, text_bytes, index_error_code
                  FROM edushift.ai_platform_source_documents
                 WHERE deleted = false
                 ORDER BY updated_at DESC, title
                """, (rs, row) -> new AiPlatformSourceResponse(
                rs.getObject("public_uuid", UUID.class), rs.getString("title"),
                rs.getString("status"), parseAudiences(rs.getString("audiences")),
                rs.getString("source_url"), rs.getString("source_version"),
                rs.getObject("effective_at", LocalDate.class),
                rs.getObject("review_after", LocalDate.class), rs.getString("original_name"),
                rs.getLong("size_bytes"), rs.getLong("text_bytes"),
                rs.getString("index_error_code")));
    }

    @Transactional(readOnly = true)
    public AiPlatformSourceReviewResponse review(UUID publicUuid) {
        UUID documentId = jdbc.query("""
                SELECT id FROM edushift.ai_platform_source_documents
                 WHERE public_uuid = ? AND deleted = false
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, publicUuid);
        if (documentId == null) throw notFound();

        List<AiPlatformSourceReviewResponse.TextChunk> chunks = jdbc.query("""
                SELECT page_number, content
                  FROM edushift.ai_platform_source_chunks
                 WHERE document_id = ? AND deleted = false
                 ORDER BY ordinal
                 LIMIT 21
                """, (rs, row) -> new AiPlatformSourceReviewResponse.TextChunk(
                rs.getObject("page_number", Integer.class), rs.getString("content")), documentId);
        int indexedChunks = jdbc.queryForObject("""
                SELECT count(*) FROM edushift.ai_platform_source_chunks
                 WHERE document_id = ? AND deleted = false
                """, Integer.class, documentId);
        boolean truncated = chunks.size() > 20;
        String textPreview = chunks.stream().limit(20)
                .map(chunk -> (chunk.pageNumber() == null ? "" : "[Página " + chunk.pageNumber() + "] ")
                        + chunk.content())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new AiPlatformSourceReviewResponse(textPreview, indexedChunks, truncated);
    }

    @Transactional(readOnly = true)
    public SourceFile openFile(UUID publicUuid) {
        SourceFileMetadata metadata = jdbc.query("""
                SELECT storage_remote_key, content_type, original_name, size_bytes
                  FROM edushift.ai_platform_source_documents
                 WHERE public_uuid = ? AND deleted = false
                """, rs -> rs.next() ? new SourceFileMetadata(
                rs.getString("storage_remote_key"), rs.getString("content_type"),
                rs.getString("original_name"), rs.getLong("size_bytes")) : null, publicUuid);
        if (metadata == null) throw notFound();
        InputStream content = storage.open(TenantIdResolver.SUPER_ADMIN_SENTINEL,
                metadata.remoteKey());
        return new SourceFile(content, metadata.contentType(), metadata.fileName(), metadata.sizeBytes());
    }

    @Transactional
    public AiPlatformSourceResponse upload(String title, String sourceUrl, String sourceVersion,
            LocalDate effectiveAt, LocalDate reviewAfter, Set<String> audiences, MultipartFile file) {
        validateMetadata(title, sourceUrl, sourceVersion, reviewAfter, audiences);
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("AI_PLATFORM_SOURCE_FILE_INVALID",
                    "Selecciona un archivo de hasta 25 MB.");
        }
        fileValidator.validate(file);
        String contentType = normalizeMime(file.getContentType());
        if (!Set.of("application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain").contains(contentType)) {
            throw new BadRequestException("AI_SOURCE_UNSUPPORTED",
                    "Solo se admiten PDF, DOCX, TXT y HTML.");
        }

        UUID publicUuid = UUID.randomUUID();
        UUID fileUuid = UUID.randomUUID();
        UUID systemTenant = TenantIdResolver.SUPER_ADMIN_SENTINEL;
        String fileName = safeFileName(file.getOriginalFilename());
        StoredObject stored;
        try {
            stored = storage.put(new StoragePutRequest(systemTenant, "edushift-system",
                    "ai-global-sources", fileUuid, fileName, contentType,
                    file.getInputStream(), file.getSize(), storageEnvResolver.resolveEnv(),
                    Instant.now()));
        } catch (IOException ex) {
            throw new BadRequestException("AI_PLATFORM_SOURCE_FILE_INVALID",
                    "No se pudo leer el archivo cargado.", ex);
        }

        try {
            jdbc.update("""
                    INSERT INTO edushift.ai_platform_source_documents
                        (id, public_uuid, title, status, audiences, source_url, source_version,
                         effective_at, review_after, storage_provider, storage_remote_key,
                         original_name, content_type, size_bytes, checksum_sha256, created_by, updated_by)
                    VALUES (?, ?, ?, 'INDEXING', ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidV7.create(), publicUuid, title.trim(), serializeAudiences(audiences),
                    sourceUrl.trim(), sourceVersion.trim(), effectiveAt, reviewAfter,
                    stored.provider().name(), stored.remoteKey(), fileName, contentType,
                    stored.sizeBytes(), stored.checksumSha256(), currentActor(), currentActor());
        } catch (RuntimeException ex) {
            storage.delete(systemTenant, stored.remoteKey());
            throw ex;
        }
        auditLogger.log(AuditAction.CREATE, "ai_platform_source", publicUuid,
                "Uploaded a national RAG source", java.util.Map.of(), "ai");
        return find(publicUuid);
    }

    @Transactional
    public AiPlatformSourceResponse publish(UUID publicUuid,
            PublishAiPlatformSourceRequest review) {
        int changed = jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET status = 'PUBLISHED', index_error_code = NULL, updated_by = ?
                 WHERE public_uuid = ? AND deleted = false AND status = 'READY'
                   AND review_after >= (now() AT TIME ZONE 'UTC')::date
                """, currentActor(), publicUuid);
        if (changed == 0) {
            if (exists(publicUuid)) {
                throw new ConflictException("AI_PLATFORM_SOURCE_NOT_READY",
                        "La fuente debe estar lista y dentro de su fecha de revisión para publicarse.");
            }
            throw notFound();
        }
        auditLogger.log(AuditAction.UPDATE, "ai_platform_source", publicUuid,
                "Published a national RAG source", java.util.Map.of(
                        "officialUrlMatchesFile", review.officialUrlMatchesFile(),
                        "currentVersionConfirmed", review.currentVersionConfirmed(),
                        "extractedTextReviewed", review.extractedTextReviewed(),
                        "audiencesAndDatesConfirmed", review.audiencesAndDatesConfirmed()), "ai");
        return find(publicUuid);
    }

    @Transactional
    public AiPlatformSourceResponse retry(UUID publicUuid) {
        int changed = jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET status = 'INDEXING', index_error_code = NULL, updated_by = ?
                 WHERE public_uuid = ? AND deleted = false
                   AND status IN ('FAILED','NEEDS_OCR')
                """, currentActor(), publicUuid);
        if (changed == 0) {
            if (exists(publicUuid)) {
                throw new ConflictException("AI_PLATFORM_SOURCE_NOT_RETRYABLE",
                        "Solo se puede reintentar una fuente fallida o sin texto.");
            }
            throw notFound();
        }
        auditLogger.log(AuditAction.UPDATE, "ai_platform_source", publicUuid,
                "Retried indexing a national RAG source", java.util.Map.of(), "ai");
        return find(publicUuid);
    }

    @Transactional
    public AiPlatformSourceResponse reindex(UUID publicUuid) {
        int changed = jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET status = 'INDEXING', index_error_code = NULL, updated_by = ?
                 WHERE public_uuid = ? AND deleted = false
                   AND status IN ('READY','PUBLISHED','FAILED','NEEDS_OCR')
                """, currentActor(), publicUuid);
        if (changed == 0) {
            if (exists(publicUuid)) {
                throw new ConflictException("AI_PLATFORM_SOURCE_NOT_REINDEXABLE",
                        "La fuente ya se está procesando o fue retirada.");
            }
            throw notFound();
        }
        auditLogger.log(AuditAction.UPDATE, "ai_platform_source", publicUuid,
                "Queued reindexing of a national RAG source", java.util.Map.of(), "ai");
        return find(publicUuid);
    }

    @Transactional
    public void retire(UUID publicUuid) {
        int changed = jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET status = 'RETIRED', updated_by = ?
                 WHERE public_uuid = ? AND deleted = false AND status <> 'RETIRED'
                """, currentActor(), publicUuid);
        if (changed == 0 && !exists(publicUuid)) throw notFound();
        if (changed > 0) {
            auditLogger.log(AuditAction.DELETE, "ai_platform_source", publicUuid,
                    "Retired a national RAG source", java.util.Map.of(), "ai");
        }
    }

    private AiPlatformSourceResponse find(UUID publicUuid) {
        return jdbc.query("""
                SELECT public_uuid, title, status, audiences::text AS audiences,
                       source_url, source_version, effective_at, review_after,
                       original_name, size_bytes, text_bytes, index_error_code
                  FROM edushift.ai_platform_source_documents
                 WHERE public_uuid = ? AND deleted = false
                """, rs -> rs.next() ? new AiPlatformSourceResponse(
                rs.getObject("public_uuid", UUID.class), rs.getString("title"),
                rs.getString("status"), parseAudiences(rs.getString("audiences")),
                rs.getString("source_url"), rs.getString("source_version"),
                rs.getObject("effective_at", LocalDate.class),
                rs.getObject("review_after", LocalDate.class), rs.getString("original_name"),
                rs.getLong("size_bytes"), rs.getLong("text_bytes"),
                rs.getString("index_error_code")) : null, publicUuid);
    }

    private boolean exists(UUID publicUuid) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM edushift.ai_platform_source_documents
                                WHERE public_uuid = ? AND deleted = false)
                """, Boolean.class, publicUuid);
        return Boolean.TRUE.equals(exists);
    }

    private static void validateMetadata(String title, String sourceUrl, String sourceVersion,
            LocalDate reviewAfter, Set<String> audiences) {
        if (title == null || title.isBlank() || title.trim().length() > 240
                || sourceUrl == null || sourceUrl.trim().length() > 1000
                || sourceVersion == null || sourceVersion.isBlank() || sourceVersion.trim().length() > 120
                || reviewAfter == null || reviewAfter.isBefore(LocalDate.now(ZoneOffset.UTC))
                || audiences == null || audiences.isEmpty()) {
            throw new BadRequestException("AI_PLATFORM_SOURCE_METADATA_INVALID",
                    "Título, versión, revisión futura o actual y audiencia son obligatorios.");
        }
        if (!Set.of("STUDENT", "TEACHER", "FAMILY").containsAll(audiences)) {
            throw new BadRequestException("AI_PLATFORM_SOURCE_AUDIENCE_INVALID",
                    "La audiencia debe ser STUDENT, TEACHER o FAMILY.");
        }
        try {
            URI uri = URI.create(sourceUrl == null ? "" : sourceUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean official = host.equals("gob.pe") || host.endsWith(".gob.pe")
                    || host.equals("minedu.gob.pe") || host.endsWith(".minedu.gob.pe")
                    || host.equals("elperuano.pe") || host.endsWith(".elperuano.pe");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !official
                    || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("AI_PLATFORM_SOURCE_URL_INVALID",
                    "La URL debe pertenecer a un dominio oficial peruano HTTPS.");
        }
    }

    private String serializeAudiences(Set<String> audiences) {
        try {
            return objectMapper.writeValueAsString(audiences.stream().sorted().toList());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot encode source audiences", ex);
        }
    }

    private List<String> parseAudiences(String audiences) {
        try {
            return objectMapper.readValue(audiences, STRING_LIST);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read source audiences", ex);
        }
    }

    private UUID currentActor() {
        return currentUserProvider.currentUserId().orElse(null);
    }

    private static String normalizeMime(String mime) {
        return mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }

    private static String safeFileName(String filename) {
        String safe = filename == null ? "documento" : filename.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (safe.isBlank()) safe = "documento";
        return safe.length() <= 240 ? safe : safe.substring(safe.length() - 240);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("AI_PLATFORM_SOURCE_NOT_FOUND", "Fuente de EduShift no encontrada.");
    }

    private record SourceFileMetadata(String remoteKey, String contentType,
                                      String fileName, long sizeBytes) {}

    public record SourceFile(InputStream content, String contentType,
                             String fileName, long sizeBytes) {}
}
