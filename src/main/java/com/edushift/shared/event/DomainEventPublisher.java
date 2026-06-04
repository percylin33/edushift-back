package com.edushift.shared.event;

/**
 * Port for publishing domain events without coupling modules to Spring APIs.
 */
public interface DomainEventPublisher {

	void publish(DomainEvent event);

}
