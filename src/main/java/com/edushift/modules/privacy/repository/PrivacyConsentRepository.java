package com.edushift.modules.privacy.repository;

import com.edushift.modules.privacy.entity.PrivacyConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

	Optional<PrivacyConsent> findTopByStudentIdAndGuardianUserIdOrderByGrantedAtDesc(
			UUID studentId, UUID guardianUserId);

	List<PrivacyConsent> findByGuardianUserId(UUID guardianUserId);

	Optional<PrivacyConsent> findByPublicUuid(UUID publicUuid);
}
