/**
 * Development-only data initializers.
 * <p>
 * All beans in this package are gated by {@code @Profile("dev")} (or similar)
 * so they NEVER run in {@code staging} or {@code prod}. They produce
 * deterministic, idempotent seeds suitable for local development and
 * integration tests.
 *
 * <p>Production data must come from Flyway migrations or proper admin
 * onboarding — not from this package.
 */
package com.edushift.infrastructure.seed;
