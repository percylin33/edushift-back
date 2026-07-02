package com.edushift.modules.files.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound response to {@link UploadRequest} (V50 signed-URL flow).
 *
 * <p>The FE uses {@code uploadUrl} to {@code PUT} the bytes directly to
 * Firebase, then calls {@code POST /api/v1/files/{publicUuid}/confirm}
 * with {@code sizeBytes} + {@code checksumSha256} so the BE can flip the
 * row from {@code PENDING} to {@code READY}.</p>
 *
 * <p>For the LOCAL_FS provider the {@code uploadUrl} is {@code null}
 * (the signed-PUT flow is not supported on disk); the FE must fall back
 * to the BE-proxied multipart endpoint {@code POST /api/v1/files}.</p>
 *
 * @param provider          which provider owns the bytes (FIREBASE / LOCAL_FS)
 * @param publicUuid        row identifier; the FE echoes it back on confirm
 * @param uploadUrl         signed PUT URL, or {@code null} for LOCAL_FS
 * @param expiresAt         instant the signed URL stops working
 * @param requiredHeaders   headers the client MUST send verbatim on the PUT
 *                          (e.g. {@code Content-Type}). Empty for LOCAL_FS.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadRequestResponse(
        String provider,
        UUID publicUuid,
        String uploadUrl,
        Instant expiresAt,
        java.util.Map<String, String> requiredHeaders
) { }