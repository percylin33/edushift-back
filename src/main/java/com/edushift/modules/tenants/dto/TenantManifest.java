package com.edushift.modules.tenants.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Public web-app installation metadata for one institution. */
public record TenantManifest(
		String id,
		String name,
		@JsonProperty("short_name") String shortName,
		String description,
		@JsonProperty("start_url") String startUrl,
		String scope,
		String display,
		String orientation,
		@JsonProperty("background_color") String backgroundColor,
		@JsonProperty("theme_color") String themeColor,
		String lang,
		List<String> categories,
		List<Icon> icons
) {
	public record Icon(String src, String sizes, String type, String purpose) {}
}
