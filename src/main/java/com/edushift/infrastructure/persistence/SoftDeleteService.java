package com.edushift.infrastructure.persistence;

import com.edushift.shared.domain.BaseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic operations to work with soft-deleted rows that are hidden by the
 * global {@code @SQLRestriction("deleted = false")} on {@link BaseEntity}.
 * <p>
 * Uses parameterized native SQL (the table name is read from the
 * {@code @Table} annotation, never from user input — safe from SQL injection).
 *
 * <h3>Typical use</h3>
 * <pre>{@code
 * softDeleteService.softDelete(Student.class, studentId);   // mark deleted
 * softDeleteService.findAnyById(Student.class, studentId);  // load (incl. deleted)
 * softDeleteService.restore(Student.class, studentId);      // undo
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class SoftDeleteService {

	private final EntityManager em;

	/**
	 * Soft-deletes a row by id. Prefer {@code repository.deleteById(id)} when the
	 * entity declares {@code @SQLDelete}; use this when you need an explicit, single
	 * UPDATE without first loading the entity.
	 */
	@Transactional
	public <T extends BaseEntity> int softDelete(Class<T> entityClass, UUID id) {
		return execute(entityClass,
				"UPDATE %s SET deleted = true, updated_at = NOW() WHERE id = :id AND deleted = false", id);
	}

	/**
	 * Restores a soft-deleted row.
	 *
	 * @return {@code 1} if a row was restored, {@code 0} otherwise
	 */
	@Transactional
	public <T extends BaseEntity> int restore(Class<T> entityClass, UUID id) {
		return execute(entityClass,
				"UPDATE %s SET deleted = false, updated_at = NOW() WHERE id = :id AND deleted = true", id);
	}

	/**
	 * Loads a row by id <strong>including</strong> soft-deleted rows.
	 */
	@Transactional(readOnly = true)
	public <T extends BaseEntity> Optional<T> findAnyById(Class<T> entityClass, UUID id) {
		String tableRef = qualifiedTableName(entityClass);
		@SuppressWarnings("unchecked")
		List<T> results = em.createNativeQuery(
						"SELECT * FROM " + tableRef + " WHERE id = :id", entityClass)
				.setParameter("id", id)
				.getResultList();
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/**
	 * Lists soft-deleted rows for the given entity type (use with caution on
	 * large tables; add pagination at the call site).
	 */
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public <T extends BaseEntity> List<T> findAllDeleted(Class<T> entityClass) {
		String tableRef = qualifiedTableName(entityClass);
		return em.createNativeQuery(
						"SELECT * FROM " + tableRef + " WHERE deleted = true ORDER BY updated_at DESC",
						entityClass)
				.getResultList();
	}

	/**
	 * Hard-deletes a row, bypassing the soft-delete strategy. Use only for
	 * compliance cleanup (e.g. GDPR erasure) or scheduled purge jobs.
	 */
	@Transactional
	public <T extends BaseEntity> int hardDelete(Class<T> entityClass, UUID id) {
		String tableRef = qualifiedTableName(entityClass);
		return em.createNativeQuery("DELETE FROM " + tableRef + " WHERE id = :id")
				.setParameter("id", id)
				.executeUpdate();
	}

	private <T extends BaseEntity> int execute(Class<T> entityClass, String sqlTemplate, UUID id) {
		String tableRef = qualifiedTableName(entityClass);
		Query query = em.createNativeQuery(sqlTemplate.formatted(tableRef));
		query.setParameter("id", id);
		return query.executeUpdate();
	}

	private static String qualifiedTableName(Class<?> entityClass) {
		Table table = entityClass.getAnnotation(Table.class);
		if (table == null) {
			throw new IllegalArgumentException(
					"@Table annotation is required on " + entityClass.getName());
		}
		String name = table.name();
		if (name.isBlank()) {
			throw new IllegalArgumentException(
					"@Table.name is required on " + entityClass.getName());
		}
		String schema = table.schema();
		return (schema == null || schema.isBlank()) ? name : schema + "." + name;
	}

}
