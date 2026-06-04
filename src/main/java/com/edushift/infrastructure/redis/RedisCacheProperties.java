package com.edushift.infrastructure.redis;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "edushift.redis")
public class RedisCacheProperties {

	private boolean enabled = true;

	private String keyPrefix = "edushift:";

	private Duration defaultTtl = Duration.ofMinutes(10);

	private final CacheTtl cache = new CacheTtl();

	@Getter
	@Setter
	public static class CacheTtl {

		private Duration dashboards = Duration.ofMinutes(5);

		private Duration permissions = Duration.ofMinutes(15);

		private Duration tenantConfig = Duration.ofHours(1);

		private Duration aiContext = Duration.ofMinutes(30);

	}

}
