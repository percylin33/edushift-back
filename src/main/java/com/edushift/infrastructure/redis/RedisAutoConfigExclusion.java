package com.edushift.infrastructure.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Primary;

/**
 * Spring Boot 3 auto-configuration that disables Spring Data Redis
 * when {@code edushift.redis.enabled=false}.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so the {@code exclude = …} is applied <strong>before</strong> the regular
 * Spring Boot autoconfig pipeline runs. Without this, Spring Boot detects
 * {@code spring-boot-starter-data-redis} on the classpath and tries to build
 * a {@code RedisConnectionFactory} from the (empty) URL/host, failing in
 * environments where Redis is not provisioned
 * ({@code The URL '' is not valid … scheme 'null' is not supported}).
 *
 * <p>When Redis is enabled (default), this configuration is a no-op and Spring
 * Boot autoconfigures Redis normally; {@code RedisConfiguration} then provides
 * the {@link RedisCacheManager}. When Redis is disabled, this class also
 * contributes a {@link NoOpCacheManager} as the primary {@link CacheManager}
 * so services annotated with {@code @Cacheable} (e.g.
 * {@code PromptManagementService}) keep resolving their cache dependency and
 * the application starts cleanly with caches as no-op (constant miss).
 */
@AutoConfiguration(before = RedisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "edushift.redis", name = "enabled", havingValue = "false")
public class RedisAutoConfigExclusion {

	@Bean
	@Primary
	CacheManager cacheManager() {
		return new NoOpCacheManager();
	}
}
