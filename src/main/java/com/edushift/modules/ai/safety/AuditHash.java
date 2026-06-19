package com.edushift.modules.ai.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Audit hash helper (Sprint 8 / SEC-8.1).
 *
 * <p>Computes a SHA-256 hex digest of the input string. Used to
 * detect abuse (same input sent N times in a row, dump of
 * copyrighted text, etc.) and to give operators a way to grep
 * across the audit log without storing PII.</p>
 */
public final class AuditHash {

    private AuditHash() {}

    /**
     * SHA-256 hex digest of {@code text} (UTF-8 bytes). Returns
     * {@code null} on null input.
     */
    public static String sha256Hex(String text) {
        if (text == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JRE; this is impossible.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
