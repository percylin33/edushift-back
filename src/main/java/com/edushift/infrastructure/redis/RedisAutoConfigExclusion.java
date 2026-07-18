package com.edushift.infrastructure.redis;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Apaga la autoconfiguración de Redis de Spring Boot cuando
 * {@code edushift.redis.enabled=false}.
 *
 * <p>Sin esto, Spring Boot 3 detecta {@code spring-boot-starter-data-redis} en el
 * classpath e intenta construir un {@code RedisConnectionFactory} con la URL/host
 * disponibles, lo que falla en entornos donde Redis no está provisionado
 * (Render sin Key Value, CI, dev local) con
 * {@code The URL '' is not valid for configuring Spring Data Redis. The scheme 'null' is not supported}.
 *
 * <p>Además aporta un {@link NoOpCacheManager} como bean primario para que los
 * servicios anotados con {@code @Cacheable} (p. ej. {@code PromptManagementService})
 * sigan resolviendo su dependencia sin necesidad de un Redis real. Los caches
 * quedan como no-op (miss constante) y no rompen el arranque.
 *
 * <p>Cuando {@code edushift.redis.enabled=true} (default), esta clase es no-op y
 * Spring Boot autoconfigura Redis normalmente, y {@code RedisConfiguration} crea
 * el {@code RedisCacheManager} real.
 */
@Configuration
@ConditionalOnProperty(prefix = "edushift.redis", name = "enabled", havingValue = "false")
@EnableAutoConfiguration(exclude = {
		RedisAutoConfiguration.class,
		RedisRepositoriesAutoConfiguration.class
})
public class RedisAutoConfigExclusion {

	@Bean
	@Primary
	CacheManager cacheManager() {
		return new NoOpCacheManager();
	}
}
