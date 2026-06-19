package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Notification template engine (Sprint 9 / BE-9.1, ADR-9.2).
 *
 * <p>Two responsibilities:</p>
 * <ol>
 *   <li><b>Sanitize</b> HTML bodies on write (allowlist of safe tags) so
 *       a malicious or careless TENANT_ADMIN can't inject {@code <script>}
 *       or {@code onclick} handlers.</li>
 *   <li><b>Render</b> at send time: replace {@code {{key}}} placeholders
 *       with values from a JSON payload.</li>
 * </ol>
 *
 * <h3>Allowlist (jsoup Safelist)</h3>
 * We extend the {@code basic} safelist with a few tags that are useful
 * for notification bodies (tables, lists, links) and then constrain the
 * link protocol to {@code http(s)} and {@code mailto}. Anything not in
 * the safelist is stripped on the way in. Subjects are plain text, no
 * HTML allowed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTemplateEngine {

    /** {@code {{key}}} placeholder, case-sensitive, no spaces inside braces. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_.\\-]+)\\}\\}");

    /**
     * Safelist: basic formatting + tables + lists + safe links.
     * We deliberately exclude {@code <script>}, {@code <style>},
     * {@code <iframe>}, {@code onclick}, {@code onerror}, etc.
     */
    private static final Safelist SAFE = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody", "tr", "th", "td",
                    "ul", "ol", "li", "p", "br", "hr", "div", "span", "strong", "em", "u")
            .addAttributes("a", "href", "title", "rel", "target")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addProtocols("a", "href", "http", "https", "mailto")
            .preserveRelativeLinks(false);

    private final ObjectMapper objectMapper;

    /**
     * Sanitize the body HTML for safe storage. Strips dangerous tags,
     * attributes, and protocols. Returns the cleaned HTML.
     */
    public String sanitizeBody(String html) {
        return sanitizeBodyStatic(html);
    }

    /**
     * Static variant (Sprint 10 / DEBT-9-FE-1 defense in depth).
     * DTOs and other non-Spring callers can re-sanitize at the
     * serialization boundary without injecting the bean.
     */
    public static String sanitizeBodyStatic(String html) {
        if (html == null) return "";
        return Jsoup.clean(html, SAFE);
    }

    /**
     * Sanitize the subject. Subjects are plain text — strip ALL HTML tags.
     * Truncated to 200 chars (matches DB column).
     */
    public String sanitizeSubject(String subject) {
        if (subject == null) return "";
        String cleaned = Jsoup.parse(subject).text();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    /**
     * Render the template: replace every {@code {{key}}} with the
     * value from {@code payloadJson}. Unknown keys are left as
     * {@code ??key??} so the sender can see what went wrong.
     *
     * <p>For HTML bodies, the substitution is text-only (no HTML
     * escape): the template author is responsible for the surrounding
     * HTML. JSON values are stringified; nested objects become
     * {@code toString()}.</p>
     */
    public Rendered render(NotificationTemplate template, String payloadJson) {
        Map<String, String> values = flattenJson(payloadJson);
        String subject = interpolate(template.getSubject(), values);
        String body    = interpolate(template.getBodyHtml(), values);
        return new Rendered(subject, body);
    }

    private static String interpolate(String template, Map<String, String> values) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder(template.length());
        while (m.find()) {
            String key = m.group(1);
            String value = values.get(key);
            String replacement = value == null ? "??" + key + "??" : Matcher.quoteReplacement(value);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Flatten a JSON payload into a {@code Map<String,String>}. Supports
     * top-level scalar fields and dotted paths (e.g. {@code "student.name"}).
     */
    private Map<String, String> flattenJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            java.util.Map<String, String> out = new java.util.HashMap<>();
            walk(root, "", out);
            return out;
        } catch (Exception e) {
            log.warn("[TemplateEngine] failed to parse payload: {}", e.getMessage());
            return Map.of();
        }
    }

    private static void walk(JsonNode node, String path, java.util.Map<String, String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                walk(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out);
            }
        } else if (node.isArray()) {
            // Arrays as comma-joined strings (good for {{roles}} etc).
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(node.get(i).asText());
            }
            out.put(path, sb.toString());
        } else if (node.isNull()) {
            out.put(path, "");
        } else {
            out.put(path, node.asText());
        }
    }

    /**
     * Result of {@link #render}.
     */
    public record Rendered(String subject, String bodyHtml) {}
}
