package com.edushift.infrastructure.redis;

/**
 * Builds consistent Redis keys for imperative {@link org.springframework.data.redis.core.RedisTemplate} usage.
 */
public final class RedisKeyBuilder {

	private final String keyPrefix;

	public RedisKeyBuilder(String keyPrefix) {
		this.keyPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
	}

	public String cacheKey(String cacheName, String tenantId, String key) {
		return keyPrefix + cacheName + ":" + tenantId + ":" + key;
	}

	public String cacheKey(String cacheName, String key) {
		return keyPrefix + cacheName + ":" + key;
	}

}
