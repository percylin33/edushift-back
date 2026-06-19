package com.edushift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EduShift backend entry point.
 *
 * <p>{@link EnableScheduling} is needed by the AI sweeper
 * ({@code com.edushift.modules.ai.job.AiSweeper}, DEBT-BE-7C-4) and
 * any future cron-style jobs. {@code @EnableAsync} lives in
 * {@code AsyncConfiguration} so the executor beans sit next to their
 * configuration.</p>
 */
@SpringBootApplication(scanBasePackages = "com.edushift")
@ConfigurationPropertiesScan(basePackages = "com.edushift")
@EnableCaching
@EnableScheduling
public class EduShiftApplication {

	public static void main(String[] args) {
		SpringApplication.run(EduShiftApplication.class, args);
	}

}
