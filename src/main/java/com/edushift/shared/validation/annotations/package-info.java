/**
 * Reusable {@link jakarta.validation.Constraint} annotations.
 * <p>
 * Each annotation here is paired with a {@link
 * jakarta.validation.ConstraintValidator} in {@code shared.validation.validators}.
 * Composite annotations (no validator class) compose existing constraints.
 * <p>
 * Default messages are externalised in {@code ValidationMessages.properties}
 * under keys of the form {@code edushift.validation.<AnnotationName>.message}.
 */
package com.edushift.shared.validation.annotations;
