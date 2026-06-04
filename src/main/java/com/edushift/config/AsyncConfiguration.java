package com.edushift.config;

import com.edushift.infrastructure.async.ContextPropagatingTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async executors used across modules.
 * <p>
 * All pools are decorated with {@link ContextPropagatingTaskDecorator} so the
 * caller's MDC (correlationId, traceId, tenantId, userId) and
 * {@link com.edushift.shared.multitenancy.TenantContext} flow into worker
 * threads. This guarantees audit / request / AI logs keep the originating
 * request's correlation id even when persisted asynchronously.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

	@Bean(name = "domainEventExecutor")
	Executor domainEventExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("domain-event-");
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.initialize();
		return executor;
	}

}
