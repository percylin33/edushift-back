package com.edushift.modules.help.repository;

import com.edushift.modules.help.entity.HelpFeedback;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HelpFeedbackRepository extends JpaRepository<HelpFeedback, UUID> {

    @Query("""
            SELECT f FROM HelpFeedback f
            WHERE f.tenantId = :tenantId
              AND f.userId = :userId
              AND f.role = :role
              AND f.deleted = false
            ORDER BY f.createdAt DESC
            """)
    List<HelpFeedback> findByUserRole(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("role") String role);
}