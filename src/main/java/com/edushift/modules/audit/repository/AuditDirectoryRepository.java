package com.edushift.modules.audit.repository;

import com.edushift.modules.audit.entity.AuditLog;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only cross-tenant labels used exclusively by the SUPER_ADMIN audit view.
 * The caller is protected by AuditController's class-level role requirement.
 */
public interface AuditDirectoryRepository extends Repository<AuditLog, UUID> {

	@Query(value = """
			SELECT u.public_uuid AS actorId, u.first_name AS firstName,
			       u.last_name AS lastName, u.email AS email
			FROM edushift.users u
			WHERE u.public_uuid IN (:actorIds) AND u.deleted = false
			""", nativeQuery = true)
	List<ActorLabel> findActorLabels(@Param("actorIds") Collection<UUID> actorIds);

	interface ActorLabel {
		UUID getActorId();
		String getFirstName();
		String getLastName();
		String getEmail();
	}
}
