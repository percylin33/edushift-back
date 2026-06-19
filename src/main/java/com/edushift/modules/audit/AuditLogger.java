package com.edushift.modules.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audit logger (Sprint 1+ / DEBT-USR-3 placeholder).
 *
 * <p>Today: structured SLF4J log line. Tomorrow: a dedicated
 * {@code audit_events} table (DEBT-USR-3).</p>
 *
 * <p>The contract:</p>
 * <ul>
 *   <li>NEVER log PII (no emails, phone, DNI).</li>
 *   <li>Always include: tenant_id, user_id (if any), action, target, ip, timestamp.</li>
 *   <li>Append-only — no reads, no updates.</li>
 * </ul>
 */
@Component
@Slf4j
public class AuditLogger {

    public void log(String action, String target, Map<String, ?> context) {
        var sb = new StringBuilder("AUDIT action=").append(action)
                .append(" target=").append(target);
        if (context != null) {
            for (var e : context.entrySet()) {
                sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
            }
        }
        log.info(sb.toString());
    }

    public void log(String action, String target) {
        log(action, target, new LinkedHashMap<>());
    }

    /** Helper to add a key to an existing map (immutable-style). */
    public static Map<String, Object> ctx(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("ctx() requires key/value pairs");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
