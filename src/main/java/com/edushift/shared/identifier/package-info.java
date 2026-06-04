/**
 * Identifier strategy for EduShift entities.
 * <p>
 * <strong>UUIDv7</strong> (RFC 9562) is the canonical id format across the modular
 * monolith and any future microservices:
 * <ul>
 *   <li>Generated client-side (Hibernate, before INSERT) — no coordination</li>
 *   <li>Time-ordered → ascending B-tree inserts, good cache locality</li>
 *   <li>Embedded creation timestamp recoverable from the id itself</li>
 *   <li>Globally unique, safe to expose in URLs, immune to enumeration attacks</li>
 *   <li>Maps to PostgreSQL's native {@code uuid} column type (16 bytes)</li>
 * </ul>
 * Apply with {@link com.edushift.shared.identifier.UuidV7Id @UuidV7Id} on the
 * {@code @Id} field. All entities that extend
 * {@link com.edushift.shared.domain.BaseEntity} inherit this strategy.
 */
package com.edushift.shared.identifier;
