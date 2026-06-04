package com.edushift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.edushift")
@ConfigurationPropertiesScan(basePackages = "com.edushift")
@EnableCaching
public class EduShiftApplication {

	public static void main(String[] args) {
		SpringApplication.run(EduShiftApplication.class, args);
	}

}
