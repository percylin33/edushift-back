package com.edushift.modules.help.repository;

import com.edushift.modules.help.entity.HelpProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HelpProgressRepository extends JpaRepository<HelpProgress, UUID> {

    @Query("""
            SELECT p FROM HelpProgress p
            WHERE p.tenantId = :tenantId
              AND p.userId = :userId
              AND p.role = :role
              AND p.chapterFile = :chapterFile
              AND p.deleted = false
            """)
    List<HelpProgress> findByUserChapter(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("chapterFile") String chapterFile);

    @Query("""
            SELECT p FROM HelpProgress p
            WHERE p.tenantId = :tenantId
              AND p.userId = :userId
              AND p.role = :role
              AND p.chapterFile = :chapterFile
              AND p.itemId = :itemId
              AND p.deleted = false
            """)
    Optional<HelpProgress> findOne(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("chapterFile") String chapterFile,
            @Param("itemId") String itemId);

    @Modifying
    @Query("""
            UPDATE HelpProgress p
               SET p.deleted = true,
                   p.deletedAt = CURRENT_TIMESTAMP,
                   p.updatedAt = CURRENT_TIMESTAMP
             WHERE p.tenantId = :tenantId
               AND p.userId = :userId
               AND p.role = :role
               AND p.chapterFile = :chapterFile
               AND p.itemId = :itemId
               AND p.deleted = false
            """)
    int softDelete(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("chapterFile") String chapterFile,
            @Param("itemId") String itemId);
}