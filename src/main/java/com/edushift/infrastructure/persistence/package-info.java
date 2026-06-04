/**
 * JPA infrastructure: configuration, auditing bridge, and soft-delete service.
 * <p>
 * Soft delete is global: every entity that extends
 * {@link com.edushift.shared.domain.BaseEntity} inherits
 * {@code @SQLRestriction("deleted = false")}. To convert {@code DELETE}
 * statements into soft deletes, each concrete entity must declare
 * {@code @SQLDelete} (table name required by JPA). See the Javadoc on
 * {@code BaseEntity} for the template.
 * <p>
 * Recovery and bypass operations live in
 * {@link com.edushift.infrastructure.persistence.SoftDeleteService}.
 */
package com.edushift.infrastructure.persistence;
