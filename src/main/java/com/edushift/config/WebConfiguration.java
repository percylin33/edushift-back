package com.edushift.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/v1", c -> {
			String pkg = c.getPackageName();
			return pkg.startsWith("com.edushift.modules")
					&& (pkg.contains(".controller")
					|| pkg.startsWith("com.edushift.modules.admin"));
		});
	}

}
