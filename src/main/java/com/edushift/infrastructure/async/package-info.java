/**
 * Async infrastructure: executor decorators that propagate request context
 * (MDC, {@link com.edushift.shared.multitenancy.TenantContext}) into worker
 * threads so async listeners keep correlation and tenant binding.
 */
package com.edushift.infrastructure.async;
