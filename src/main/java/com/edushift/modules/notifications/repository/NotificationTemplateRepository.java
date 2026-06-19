package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.NotificationTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link NotificationTemplate} (Sprint 9 / BE-9.1).
 *
 * <p>All queries are auto-filtered by Hibernate {@code @TenantId}.
 * Cross-tenant lookups return empty by construction.</p>
 */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /**
     * Find a template by its stable key + locale in the current tenant.
     * Returns empty if no template exists (the {@code NotificationService}
     * treats this as {@code FAILED} and logs a warning).
     */
    @Query("""
            SELECT t FROM NotificationTemplate t
            WHERE t.templateKey = :key
              AND t.locale = :locale
            """)
    Optional<NotificationTemplate> findByKeyAndLocale(
            @Param("key") String key,
            @Param("locale") String locale);

    /** All templates in the tenant, ordered by key. Used by the admin UI. */
    @Query("""
            SELECT t FROM NotificationTemplate t
            ORDER BY t.templateKey ASC, t.locale ASC
            """)
    List<NotificationTemplate> findAllOrdered();

    /** Update the body + bump version (for the editor). */
    default NotificationTemplate saveWithVersionBump(NotificationTemplate t) {
        t.setVersion(t.getVersion() + 1);
        return save(t);
    }
}
