package com.edushift.modules.admin.tenants;

import com.edushift.modules.admin.tenants.dto.AdminTenantAiSettingsResponse;
import java.util.UUID;

public interface AdminTenantAiSettingsService {

	AdminTenantAiSettingsResponse get(UUID tenantPublicUuid);

	AdminTenantAiSettingsResponse update(UUID tenantPublicUuid, boolean enabled, UUID actorUuid);
}
