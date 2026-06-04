package com.edushift.shared.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class AbstractDomainEvent implements DomainEvent {

	private final UUID eventId = UUID.randomUUID();

	private final Instant occurredAt = Instant.now();

	private final String sourceModule;

	protected AbstractDomainEvent(String sourceModule) {
		this.sourceModule = sourceModule;
	}

}
