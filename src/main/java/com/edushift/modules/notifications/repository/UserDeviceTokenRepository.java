package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.UserDeviceToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

	Optional<UserDeviceToken> findByToken(String token);

	List<UserDeviceToken> findByTenantIdAndUserPublicUuidAndActiveTrueOrderByLastSeenAtDesc(
			UUID tenantId, UUID userPublicUuid);

	List<UserDeviceToken> findByTokenIn(List<String> tokens);
}