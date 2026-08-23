package com.edushift.modules.me.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * QR credential envelope for {@code GET /me/qr} and {@code POST /me/qr/reveal}.
 * {@code svgDataUri} is only populated on reveal so a page view does not rotate the token.
 */
public record MeQrResponse(
        UUID studentPublicUuid,
        Instant issuedAt,
        boolean hasCredential,
        String svgDataUri
) {
}
