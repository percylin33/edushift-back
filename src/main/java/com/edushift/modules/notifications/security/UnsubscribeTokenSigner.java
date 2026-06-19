package com.edushift.modules.notifications.security;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-based unsubscribe token signer (Sprint 9 / SEC-9.1, refactored
 * Sprint 10 / DEBT-9-SEC-2).
 *
 * <p>Each email footer contains a one-click unsubscribe link with
 * a signed token. The link looks like:</p>
 *
 * <pre>
 *   https://app.edushift.com/unsubscribe?token={token}
 * </pre>
 *
 * <p>The token is an HMAC-SHA256 of {@code userId|channel|category|expiry}
 * keyed with a server-side secret. On click, the FE sends the token
 * to the BE which verifies the signature and disables the preference
 * row. Without the secret, an attacker can't forge a "disable
 * someone's notifications" link.</p>
 *
 * <h3>Why HMAC, not JWT</h3>
 * Tokens are short-lived (24h) and one-shot (the user clicks
 * exactly once). JWT is overkill — we don't need a header
 * (alg/kid) or a body with claims. A compact HMAC over the four
 * params (user, channel, category, expiry) is simpler and harder
 * to misuse.</p>
 *
 * <h3>Why a separate secret</h3>
 * The signing key is dedicated to unsubscribe links. If it ever
 * leaks, we rotate just this key — the JWT and password secrets
 * stay safe.
 *
 * <h3>Sprint 10 / DEBT-9-SEC-2</h3>
 * The signer now owns the URL construction ({@link #buildUrl}) so
 * callers don't need to inject the controller (which was a circular
 * dependency smell). The renderer lives in
 * {@code UnsubscribePageRenderer}.
 */
@Component
@Slf4j
public class UnsubscribeTokenSigner {

    private final byte[] secret;
    private final String frontendUrl;
    private final long tokenTtlHours;

    public UnsubscribeTokenSigner(
            @Value("${app.notifications.email.unsubscribe-secret:}") String secret,
            @Value("${app.notifications.email.frontend-url:https://app.edushift.com}") String frontendUrl,
            @Value("${app.notifications.email.unsubscribe-ttl-hours:24}") long tokenTtlHours) {
        // Dev fallback: a deterministic test secret. Real deployments
        // MUST set app.notifications.email.unsubscribe-secret.
        if (secret == null || secret.isBlank()) {
            log.warn("[Unsubscribe] using default secret — set app.notifications.email.unsubscribe-secret in production");
            secret = "edushift-dev-unsubscribe-secret-do-not-use-in-prod";
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.frontendUrl = frontendUrl;
        this.tokenTtlHours = tokenTtlHours;
    }

    /**
     * Sign a token. {@code expiryEpochMs} is the absolute deadline
     * (after which the link is rejected with 410 Gone).
     */
    public String sign(UUID userId, Channel channel, Category category, long expiryEpochMs) {
        String payload = userId + "|" + channel.name() + "|" + category.name() + "|" + expiryEpochMs;
        String sig = hmacHex(payload);
        return payload + "|" + sig;
    }

    /**
     * Verify a token. Returns the parsed fields, or throws
     * {@link SecurityException} if the signature doesn't match or
     * the token is expired.
     */
    public Parsed verify(String token) {
        if (token == null) throw new SecurityException("missing token");
        String[] parts = token.split("\\|");
        if (parts.length != 5) throw new SecurityException("malformed token");
        String userIdStr = parts[0];
        String channelStr = parts[1];
        String categoryStr = parts[2];
        long expiry = Long.parseLong(parts[3]);
        String providedSig = parts[4];
        String payload = userIdStr + "|" + channelStr + "|" + categoryStr + "|" + expiry;
        String expectedSig = hmacHex(payload);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            throw new SecurityException("bad signature");
        }
        if (System.currentTimeMillis() > expiry) {
            throw new SecurityException("token expired");
        }
        return new Parsed(UUID.fromString(userIdStr),
                Channel.valueOf(channelStr),
                Category.valueOf(categoryStr),
                expiry);
    }

    /**
     * Build a fresh signed token with the default TTL.
     */
    public String buildToken(UUID userId, Channel channel, Category category) {
        long expiry = Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS).toEpochMilli();
        return sign(userId, channel, category, expiry);
    }

    /**
     * Build the full unsubscribe URL for the email footer.
     * Centralised here so the {@code EmailSender} doesn't need to
     * inject the controller (DEBT-9-SEC-2).
     */
    public String buildUrl(UUID userId, Channel channel, Category category) {
        return frontendUrl + "/api/v1/unsubscribe?token=" + buildToken(userId, channel, category);
    }

    private String hmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new SecurityException("HMAC failure: " + e.getMessage(), e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public record Parsed(UUID userId, Channel channel, Category category, long expiryEpochMs) {}
}
