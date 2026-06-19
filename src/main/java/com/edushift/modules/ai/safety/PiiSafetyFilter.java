package com.edushift.modules.ai.safety;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * PII (Personally Identifiable Information) safety filter
 * (Sprint 8 / SEC-8.1).
 *
 * <p>Scans LLM input/output strings and masks common PII before they
 * are persisted to {@code ai_generations} / {@code ai_chat_messages}
 * or rendered to the FE. Targets the Peruvian education context:</p>
 *
 * <ul>
 *   <li><b>DNI Peruano</b> — 8-digit numeric string (no leading zero
 *       validation; we keep the mask generic).</li>
 *   <li><b>Email</b> — RFC-5321-lite pattern (good enough for masking;
 *       not a full validation).</li>
 *   <li><b>Phone</b> — Peruvian mobile format {@code 9XX-XXX-XXX} or
 *       any 9-digit sequence with optional separators.</li>
 *   <li><b>Credit card</b> — 13-19 digit sequence (Luhn not enforced;
 *       we just mask).</li>
 * </ul>
 *
 * <h3>Why not rely on the LLM</h3>
 * LLMs can leak PII in their output (training data, prompt
 * injection). The mask is applied as the last step in the
 * {@code LlmClient} pipeline so the audit row never stores raw PII,
 * even if the model accidentally generates it.
 *
 * <h3>Limitations</h3>
 * - Regex-based, so it can be tricked by unicode lookalikes
 *   (e.g. fullwidth digits). For v1, that's accepted risk; a
 *   future sprint may add a normalizer.
 * - We don't mask names. Names are PII in many jurisdictions, but
 *   masking them in session titles / lesson plans would render
 *   the content useless. The plan is to use a separate NER
 *   pass in SEC-9.x for richer masking.
 */
@Component
public class PiiSafetyFilter {

    private static final Pattern DNI = Pattern.compile("\\b\\d{8}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PE = Pattern.compile(
            "\\b9\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3}\\b");
    private static final Pattern CCARD = Pattern.compile(
            "\\b\\d[ \\-]?\\d{3,}[ \\-]?\\d{3,}[ \\-]?\\d{3,}[ \\-]?\\d{1,4}\\b");

    private static final String DNI_MASK = "XXX-XXX-XX";
    private static final String EMAIL_MASK = "[email-masked]";
    private static final String PHONE_MASK = "[phone-masked]";
    private static final String CCARD_MASK = "[card-masked]";

    /**
     * Apply all masks in one pass. Returns a new string; the input
     * is never mutated.
     */
    public String mask(String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        out = maskWith(out, CCARD, CCARD_MASK);   // cards first (most specific)
        out = maskWith(out, PHONE_PE, PHONE_MASK);
        out = maskWith(out, EMAIL, EMAIL_MASK);
        out = maskWith(out, DNI, DNI_MASK);
        return out;
    }

    private static String maskWith(String s, Pattern p, String replacement) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
