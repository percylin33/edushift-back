package com.edushift.shared.security;

import java.util.UUID;

/**
 * Reserved principals used when no authenticated user is available
 * (system jobs, migrations, scheduled tasks).
 */
public final class SystemUsers {

	/** Constant id for system-generated records and background jobs. */
	public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	/** Constant id for anonymous (unauthenticated) actions worth auditing. */
	public static final UUID ANONYMOUS_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private SystemUsers() {
	}

}
