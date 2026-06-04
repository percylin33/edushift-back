package com.edushift.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for domain events exchanged between modules inside the monolith.
 */
public interface DomainEvent {

	UUID getEventId();

	Instant getOccurredAt();

	String getSourceModule();

}
