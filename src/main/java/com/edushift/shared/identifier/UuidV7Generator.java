package com.edushift.shared.identifier;

import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

/**
 * Hibernate id generator that produces RFC 9562 UUIDv7 values before INSERT.
 * <p>
 * Activated via the {@link UuidV7Id} meta-annotation on entity id fields.
 */
public class UuidV7Generator implements BeforeExecutionGenerator {

	@Override
	public Object generate(
			SharedSessionContractImplementor session,
			Object owner,
			Object currentValue,
			EventType eventType) {
		return currentValue != null ? currentValue : UuidV7.create();
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return EnumSet.of(EventType.INSERT);
	}

}
