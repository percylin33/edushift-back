package com.edushift.modules.privacy.service;

import com.edushift.modules.privacy.dto.ConsentResponse;
import com.edushift.modules.privacy.dto.CreateConsentRequest;
import java.util.UUID;

public interface PrivacyConsentService {

	ConsentResponse recordConsent(CreateConsentRequest request, String ipAddress, String userAgent);

	ConsentResponse getLatestConsent(UUID studentPublicUuid);

	ConsentResponse revokeConsent(UUID consentPublicUuid);
}
