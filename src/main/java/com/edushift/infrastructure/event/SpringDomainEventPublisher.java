package com.edushift.infrastructure.event;

import com.edushift.shared.event.DomainEvent;
import com.edushift.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void publish(DomainEvent event) {
		applicationEventPublisher.publishEvent(event);
	}

}
