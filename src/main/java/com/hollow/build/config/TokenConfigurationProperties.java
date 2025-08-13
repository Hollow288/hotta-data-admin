package com.hollow.build.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.token")
public class TokenConfigurationProperties {
		

	@Pattern(regexp = "^[a-zA-Z0-9+/]*={0,2}$", message = "Secret key must be Base64 encoded.")
	private String secretKey;


	private Integer validityAccessToken;


	private Integer validityRefreshToken;

}