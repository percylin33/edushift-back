package com.edushift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public business contact addresses configured by deployment properties. */
@ConfigurationProperties(prefix = "edushift.contact")
public record PublicContactProperties(
		String supportEmail,
		String privacyEmail,
		String legalEmail
) {}
