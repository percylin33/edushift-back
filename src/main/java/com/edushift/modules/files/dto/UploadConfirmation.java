package com.edushift.modules.files.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Inbound confirmation that the bytes actually landed in the provider
 * (V50 signed-URL flow).
 *
 * <p>The FE computes SHA-256 on the client (Web Crypto SubtleCrypto) and
 * sends it back so the BE can store it as {@code lms_file_objects.checksum_sha256}.
 * If the client never sends this, the row stays in {@code PENDING} and
 * the housekeeping job (DEBT-7A-1) GCs it after 24h.</p>
 */
public record UploadConfirmation(
        @Positive long sizeBytes,
        @NotBlank @Size(min = 64, max = 64) String checksumSha256
) { }