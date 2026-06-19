package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link Announcement} (Sprint 9 / BE-9.4).
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("""
            SELECT a FROM Announcement a
            WHERE a.status = com.edushift.modules.notifications.entity.Announcement.Status.PUBLISHED
            ORDER BY a.publishedAt DESC
            """)
    Page<Announcement> findPublished(Pageable pageable);

    @Query("""
            SELECT a FROM Announcement a
            ORDER BY a.createdAt DESC
            """)
    Page<Announcement> findAllForAdmin(Pageable pageable);

    Optional<Announcement> findByPublicUuid(UUID publicUuid);
}
