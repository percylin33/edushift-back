package com.edushift.modules.attendance.events;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-process pub/sub for live attendance events (Sprint 18 / BE-18.6).
 *
 * <h3>Scope</h3>
 * The publisher holds one {@link CopyOnWriteArrayList} of
 * {@link SseEmitter}s per active session. When {@code AttendanceService}
 * creates or updates a record it calls
 * {@link #publishRecordCreated(UUID, AttendanceRecordResponse)}; the
 * publisher fans the payload out to every subscriber currently
 * streaming that session.
 *
 * <h3>Lifecycle</h3>
 * Subscribers are added via {@link #subscribe(UUID, SseEmitter)}.
 * The {@code AttendanceController}'s SSE endpoint configures the
 * emitter's {@code onCompletion} / {@code onTimeout} / {@code onError}
 * callbacks to call {@link #unsubscribe(UUID, SseEmitter)} so we
 * never leak stale connections. When the controller method returns
 * the emitter is "live" — the publisher holds the only strong
 * reference, so closing the HTTP response is enough to garbage-collect
 * the emitter.
 *
 * <h3>Single-tenant assumption</h3>
 * The map is keyed by {@code sessionPublicUuid} (which is globally
 * unique). Every emitter therefore only sees events for the session
 * it subscribed to. Cross-tenant leakage is impossible at this
 * layer; the {@code AttendanceController} enforces the role check
 * up front.
 *
 * <h3>Why in-process, not Redis pub/sub</h3>
 * Each backend node holds its own copy of the emitter registry. For
 * an MVP, a load balancer that pins a teacher's session to a single
 * node is acceptable; if the user moves to a multi-node
 * configuration, the publisher becomes a thin abstraction over
 * Redis pub/sub. The interface stays the same.
 */
@Component
public class AttendanceEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(AttendanceEventPublisher.class);

	/**
	 * Emitters per session. Concurrent because Spring may resolve a
	 * teacher's SSE request on a different thread than the one the
	 * publisher runs on. The values are COW so iteration is safe
	 * against concurrent mutation.
	 */
	private final java.util.concurrent.ConcurrentMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersBySession =
			new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Register a new subscriber. The caller (the controller) configures
	 * the emitter's lifecycle callbacks to call {@link #unsubscribe}
	 * — this method just adds it to the bucket.
	 */
	public void subscribe(UUID sessionPublicUuid, SseEmitter emitter) {
		emittersBySession
				.computeIfAbsent(sessionPublicUuid, k -> new CopyOnWriteArrayList<>())
				.add(emitter);
		log.debug("[attendance-events] subscriber added — session={} count={}",
				sessionPublicUuid, emittersBySession.get(sessionPublicUuid).size());
	}

	/**
	 * Remove a subscriber. Idempotent — calling twice is a no-op. The
	 * controller calls this from the emitter's onCompletion /
	 * onTimeout / onError callbacks.
	 */
	public void unsubscribe(UUID sessionPublicUuid, SseEmitter emitter) {
		List<SseEmitter> list = emittersBySession.get(sessionPublicUuid);
		if (list == null) return;
		boolean removed = list.remove(emitter);
		if (list.isEmpty()) {
			emittersBySession.remove(sessionPublicUuid);
		}
		if (removed) {
			log.debug("[attendance-events] subscriber removed — session={} remaining={}",
					sessionPublicUuid, list.size());
		}
	}

	/**
	 * Publish a "record-created" event to every subscriber of the
	 * given session. Failures on individual emitters (network
	 * timeout, broken pipe) are logged and the emitter is dropped
	 * from the bucket — we never let one bad subscriber poison the
	 * rest.
	 *
	 * <p>Idempotent re-reads (when a second scan of the same
	 * student happens before the first one has flushed) do NOT
	 * publish: the caller skips the publish when {@code wasIdempotent}
	 * is true. We don't want the UI to flash a duplicate "checked in"
	 * event.</p>
	 */
	public void publishRecordCreated(UUID sessionPublicUuid, AttendanceRecordResponse record) {
		if (record == null || Boolean.TRUE.equals(record.wasIdempotent())) {
			return;
		}
		CopyOnWriteArrayList<SseEmitter> subs = emittersBySession.get(sessionPublicUuid);
		if (subs == null || subs.isEmpty()) {
			return; // no subscribers — nothing to do
		}
		// Snapshot to avoid ConcurrentModificationException if a
		// subscriber unsubscribes mid-iteration.
		SseEmitter[] snapshot = subs.toArray(new SseEmitter[0]);
		for (SseEmitter e : snapshot) {
			try {
				e.send(SseEmitter.event()
						.name("record-created")
						.data(record));
			}
			catch (Exception ex) {
				log.debug("[attendance-events] dropping subscriber after send failure — session={} err={}",
						sessionPublicUuid, ex.toString());
				unsubscribe(sessionPublicUuid, e);
			}
		}
	}
}
