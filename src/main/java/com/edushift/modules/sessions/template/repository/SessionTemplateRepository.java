package com.edushift.modules.sessions.template.repository;

import com.edushift.modules.sessions.template.entity.SessionTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionTemplateRepository extends JpaRepository<SessionTemplate, UUID> {

    Optional<SessionTemplate> findByPublicUuid(UUID publicUuid);

    Optional<SessionTemplate> findByTemplateKey(String templateKey);

    List<SessionTemplate> findByIsSystemTrueOrderByFamilyAscNameAsc();

    List<SessionTemplate> findByIsSystemFalseOrderByIsDefaultDescFamilyAscNameAsc();

    List<SessionTemplate> findAllByOrderByIsSystemDescNameAsc();

    Optional<SessionTemplate> findByTemplateKeyAndIsSystemTrue(String templateKey);

    Optional<SessionTemplate> findByIsDefaultTrueAndIsSystemFalse();

    @Query("""
            select t from SessionTemplate t
            where t.isDefault = true
              and t.isSystem = false
              and t.publicUuid <> :excludeUuid
            """)
    List<SessionTemplate> findOtherDefaults(@Param("excludeUuid") UUID excludeUuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SessionTemplate t
            set t.isDefault = false
            where t.isDefault = true
              and t.isSystem = false
              and t.publicUuid <> :excludeUuid
            """)
    int clearOtherDefaults(@Param("excludeUuid") UUID excludeUuid);
}
