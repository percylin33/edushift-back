package com.edushift.infrastructure.redis;

import com.edushift.shared.constants.RedisCacheNames;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(RedisCacheProperties.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "edushift.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfiguration {

	@Bean
	RedisSerializer<Object> redisJsonSerializer() {
		return RedisJsonSerializerFactory.jsonSerializer();
	}

	@Bean
	RedisTemplate<String, Object> redisTemplate(
			RedisConnectionFactory connectionFactory,
			RedisSerializer<Object> redisJsonSerializer) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(StringRedisSerializer.UTF_8);
		template.setHashKeySerializer(StringRedisSerializer.UTF_8);
		template.setValueSerializer(redisJsonSerializer);
		template.setHashValueSerializer(redisJsonSerializer);
		template.setEnableTransactionSupport(false);
		template.afterPropertiesSet();
		return template;
	}

	@Bean
	RedisKeyBuilder redisKeyBuilder(RedisCacheProperties properties) {
		return new RedisKeyBuilder(properties.getKeyPrefix());
	}

	@Bean
	RedisCacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			RedisCacheProperties properties,
			RedisSerializer<Object> redisJsonSerializer) {
		RedisCacheConfiguration defaults = baseCacheConfig(properties, redisJsonSerializer)
				.entryTtl(properties.getDefaultTtl());

		Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
		perCache.put(RedisCacheNames.DASHBOARDS,
				baseCacheConfig(properties, redisJsonSerializer).entryTtl(properties.getCache().getDashboards()));
		perCache.put(RedisCacheNames.PERMISSIONS,
				baseCacheConfig(properties, redisJsonSerializer).entryTtl(properties.getCache().getPermissions()));
		perCache.put(RedisCacheNames.TENANT_CONFIG,
				baseCacheConfig(properties, redisJsonSerializer).entryTtl(properties.getCache().getTenantConfig()));
		perCache.put(RedisCacheNames.AI_CONTEXT,
				baseCacheConfig(properties, redisJsonSerializer).entryTtl(properties.getCache().getAiContext()));

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(defaults)
				.withInitialCacheConfigurations(perCache)
				.transactionAware()
				.build();
	}

	private static RedisCacheConfiguration baseCacheConfig(
			RedisCacheProperties properties,
			RedisSerializer<Object> redisJsonSerializer) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.prefixCacheNameWith(properties.getKeyPrefix())
				.serializeKeysWith(RedisSerializationContext.SerializationPair
						.fromSerializer(StringRedisSerializer.UTF_8))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(redisJsonSerializer))
				.disableCachingNullValues();
	}

}
