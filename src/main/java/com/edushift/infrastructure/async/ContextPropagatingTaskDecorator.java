package com.edushift.infrastructure.async;

import com.edushift.shared.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the calling thread's <strong>MDC</strong> (correlationId, traceId,
 * tenantId, userId, ...) and {@link TenantContext} into worker threads of any
 * {@code ThreadPoolTaskExecutor} that uses this decorator.
 * <p>
 * Without this, async listeners (audit, notifications) lose their request
 * context and emit log lines without correlation / tenant info.
 * <p>
 * The decorator captures snapshots on the caller side and restores the
 * previous state on the worker side after execution, so pooled threads never
 * leak context to subsequent tasks.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable task) {
		Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
		UUID tenantSnapshot = TenantContext.current().orElse(null);

		return () -> {
			Map<String, String> previousMdc = MDC.getCopyOfContextMap();
			UUID previousTenant = TenantContext.current().orElse(null);
			try {
				if (mdcSnapshot != null) {
					MDC.setContextMap(mdcSnapshot);
				}
				else {
					MDC.clear();
				}
				if (tenantSnapshot != null) {
					TenantContext.set(tenantSnapshot);
				}
				else {
					TenantContext.clear();
				}

				task.run();
			}
			finally {
				if (previousMdc != null) {
					MDC.setContextMap(previousMdc);
				}
				else {
					MDC.clear();
				}
				if (previousTenant != null) {
					TenantContext.set(previousTenant);
				}
				else {
					TenantContext.clear();
				}
			}
		};
	}

}
