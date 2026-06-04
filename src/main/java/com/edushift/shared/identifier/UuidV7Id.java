package com.edushift.shared.identifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * Marks a JPA {@code @Id} field as auto-generated using {@link UuidV7Generator}.
 * <p>
 * Use together with {@link jakarta.persistence.Id}:
 * <pre>{@code
 * @Id
 * @UuidV7Id
 * @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
 * private UUID id;
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@IdGeneratorType(UuidV7Generator.class)
public @interface UuidV7Id {
}
