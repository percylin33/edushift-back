/**
 * Cross-cutting validation infrastructure.
 * <p>
 * Layout:
 * <ul>
 *   <li>{@link com.edushift.shared.validation.groups} — marker interfaces for
 *       Bean Validation groups ({@code OnCreate}, {@code OnUpdate}, {@code OnPatch})</li>
 *   <li>{@link com.edushift.shared.validation.annotations} — reusable
 *       {@code @Constraint} annotations (custom and composite)</li>
 *   <li>{@link com.edushift.shared.validation.validators} — {@link
 *       jakarta.validation.ConstraintValidator} implementations</li>
 * </ul>
 * Default messages live in {@code src/main/resources/ValidationMessages.properties}.
 * Stable error codes for the public API are resolved by
 * {@link com.edushift.shared.validation.ValidationErrorCodes}.
 */
package com.edushift.shared.validation;
