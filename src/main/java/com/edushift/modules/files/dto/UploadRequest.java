package com.edushift.modules.files.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Inbound request to mint a signed upload URL (V50, docs/infra/firebase.md).
 *
 * <p>The FE sends this before pushing the bytes to Firebase; the BE
 * validates the caller's tenant + permissions and returns
 * {@link UploadRequestResponse} containing the signed URL and the
 * freshly-minted {@code publicUuid}.</p>
 *
 * @param module        logical bucket inside the storage layout
 *                      ({@code "materials"}, {@code "submissions"}, …)
 * @param originalName  filename as seen by the user
 * @param contentType   MIME the client intends to send (used to bind the
 *                      signed URL — the PUT must use this exact value)
 * @param sizeBytes     declared payload size; capped server-side against
 *                      {@code app.storage.max-file-size-bytes}
 */
public record UploadRequest(
        @NotBlank @Size(max = 64) String module,
        @NotBlank @Size(max = 255) String originalName,
        @NotBlank @Size(max = 127) String contentType,
        @Positive long sizeBytes
) { }