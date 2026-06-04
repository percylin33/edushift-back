/**
 * {@link jakarta.validation.ConstraintValidator} implementations for the
 * annotations in {@code shared.validation.annotations}.
 * <p>
 * Validators are stateless and pure — they don't query the database. For
 * uniqueness or referential checks, prefer service-level business rules
 * (raise {@link com.edushift.shared.exception.ConflictException} or
 * {@link com.edushift.shared.exception.BusinessException}).
 */
package com.edushift.shared.validation.validators;
