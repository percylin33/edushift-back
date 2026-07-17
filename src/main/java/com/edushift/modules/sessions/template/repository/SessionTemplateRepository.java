package com.edushift.modules.sessions.template.repository;

import com.edushift.modules.sessions.template.entity.SessionTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionTemplateRepository extends JpaRepository<SessionTemplate, UUID> {

    Optional<SessionTemplate> findByPublicUuid(UUID publicUuid);

    Optional<SessionTemplate> findByTemplateKey(String templateKey);

    List<SessionTemplate> findByIsSystemTrueOrderByNameAsc();

    List<SessionTemplate> findAllByOrderByIsSystemDescNameAsc();
}
