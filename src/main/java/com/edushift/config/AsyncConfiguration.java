package com.edushift.config;

import com.edushift.infrastructure.async.ContextPropagatingTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
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
@EnableScheduling
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

	/**
	 * Pool dedicated to bulk-import jobs (Excel/CSV uploads). Smaller
	 * pool than {@code domainEventExecutor} because each task can
	 * easily run for tens of seconds and bring its own DB pressure;
	 * over-parallelising risks starving the connection pool.
	 *
	 * <p>Queue capacity is intentionally low — admins are unlikely to
	 * fire dozens of imports back-to-back, and we'd rather reject the
	 * caller fast than queue up a backlog that a server restart would
	 * lose silently.
	 */
	@Bean(name = "bulkImportExecutor")
	Executor bulkImportExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("bulk-import-");
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.initialize();
		return executor;
	}

	/**
	 * Pool dedicated to long-running AI generation jobs (BE-7c.2).
	 * Sizing rationale: an LLM call typically takes 1-3s for the
	 * synchronous quiz-question flow, and up to 30-60s for larger
	 * generation jobs (rubrics, session outlines, future streaming).
	 * <ul>
	 *   <li>Core size 2 keeps 2 generations in flight on every node
	 *       — enough throughput for the expected tenant count.</li>
	 *   <li>Max size 4 gives headroom for bursts without starving the
	 *       Hikari pool (default max 10 connections, shared with the
	 *       request-serving threads).</li>
	 *   <li>Queue capacity 50 absorbs traffic spikes; if the queue
	 *       fills, the caller's {@code POST} is rejected with 503 +
	 *       a clear "AI busy, retry shortly" message rather than
	 *       silently dropped.</li>
	 * </ul>
	 * Caller-runs rejection is intentional: the controller will
	 * surface 503 to the client instead of letting generations vanish
	 * during a reboot.
	 */
	@Bean(name = "aiJobExecutor")
	Executor aiJobExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("ai-job-");
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		// When the queue is full, run on the caller thread instead of
		// dropping the task. The controller maps that to a 503 with a
		// retry hint.
		executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

	/**
	 * Single-thread scheduler for SSE heartbeats (Sprint 18 / BE-18.6).
	 * 15-second heartbeat × N concurrent subscribers = at most N/15 wake-ups
	 * per second, well within a single thread. A dedicated pool keeps
	 * heartbeats off the request-serving and AI executors — a slow
	 * SSE subscriber can't starve a real user.
	 */
	@Bean(name = "heartbeatScheduler", destroyMethod = "shutdown")
	java.util.concurrent.ScheduledExecutorService heartbeatScheduler() {
		java.util.concurrent.ScheduledThreadPoolExecutor executor =
				new java.util.concurrent.ScheduledThreadPoolExecutor(
						1, // corePoolSize — single thread, sequential heartbeats
						r -> {
							Thread t = new Thread(r, "attendance-heartbeat-");
							t.setDaemon(true); // never block JVM shutdown
							return t;
						});
		// Allow cancelled tasks to be removed from the queue so a
		// disconnected subscriber's pending heartbeats don't pile up.
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}

}
