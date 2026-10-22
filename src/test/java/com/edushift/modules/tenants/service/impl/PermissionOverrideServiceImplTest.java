package com.edushift.modules.tenants.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.security.LmsAuthorities;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionOverrideServiceImplTest {

	@Test
	void tenantAdminCannotGrantItselfDisclosurePreparation() {
		PermissionOverrideServiceImpl service = new PermissionOverrideServiceImpl(
				null, 60);

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.upsert(UUID.randomUUID(), UserRole.TENANT_ADMIN,
						LmsAuthorities.LMS_DISCLOSURE_MANAGE, true, UUID.randomUUID()));

		assertEquals("TENANT_ADMIN_DISCLOSURE_PREPARATION_DISABLED", exception.getCode());
	}
}
