package com.edushift.modules.admin.auth.dto;

import com.edushift.shared.validation.annotations.ValidPassword;

public record AdminInitialPasswordChangeRequest(
		@ValidPassword String newPassword
) {}
