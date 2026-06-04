/**
 * Shared domain primitives — mapped superclasses for module entities.
 * <p>
 * Hierarchy:
 * <pre>
 * BaseEntity              (id, createdAt, updatedAt, deleted)
 *   └── AuditableEntity   (+ createdBy, updatedBy)
 *         └── TenantAwareEntity   (+ tenantId)
 * </pre>
 * Use the most specific base for each entity:
 * <ul>
 *   <li>System / shared tables → {@code BaseEntity}</li>
 *   <li>Audited, single-tenant scope → {@code AuditableEntity}</li>
 *   <li>Multi-tenant business data → {@code TenantAwareEntity}</li>
 * </ul>
 */
package com.edushift.shared.domain;
