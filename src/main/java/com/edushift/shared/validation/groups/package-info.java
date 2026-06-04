/**
 * Bean Validation group markers used to scope constraints to specific
 * use-cases.
 * <p>
 * Apply on a controller method with {@code @Validated(OnCreate.class)} and
 * mark constraints on the DTO with {@code groups = OnCreate.class}.
 * Without an explicit group, constraints default to {@code Default.class}
 * and run for every request.
 */
package com.edushift.shared.validation.groups;
