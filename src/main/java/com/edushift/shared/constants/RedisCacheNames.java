package com.edushift.shared.constants;

/**
 * Spring Cache names backed by Redis. TTL per cache in {@code edushift.redis.cache.*}.
 */
public final class RedisCacheNames {

	public static final String DASHBOARDS = "dashboards";

	public static final String PERMISSIONS = "permissions";

	public static final String TENANT_CONFIG = "tenant-config";

	public static final String AI_CONTEXT = "ai-context";

	private RedisCacheNames() {
	}

}
