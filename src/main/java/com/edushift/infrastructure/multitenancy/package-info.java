/**
 * Multi-tenancy infrastructure: resolver, servlet filter, MVC interceptor and
 * the Hibernate discriminator hook ({@code CurrentTenantIdentifierResolver}).
 * <p>
 * Entities that extend {@code TenantAwareEntity} are filtered and populated
 * automatically thanks to Hibernate's {@code @TenantId} annotation.
 */
package com.edushift.infrastructure.multitenancy;
