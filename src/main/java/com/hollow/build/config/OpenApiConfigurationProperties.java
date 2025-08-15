package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "com.hollow")
public class OpenApiConfigurationProperties {

	private OpenAPI openApi = new OpenAPI();

	@Getter
	@Setter
	public static class OpenAPI {
		

		private boolean enabled;
		
		private String title;
		private String apiVersion;
		private String description;
		
	}

}