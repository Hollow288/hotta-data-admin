package com.hollow.build.config;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


/**
 * Token 认证配置属性类，用于绑定 JWT 密钥、有效期及限流等配置项
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.token")
public class TokenConfigurationProperties {
		

	/** JWT 签名密钥（Base64 编码） */
	@Pattern(regexp = "^[a-zA-Z0-9+/]*={0,2}$", message = "Secret key must be Base64 encoded.")
	private String secretKey;

	/** 访问令牌有效期（秒） */
	private Integer validityAccessToken;

	/** 刷新令牌有效期（秒） */
	private Integer validityRefreshToken;

	/** 单用户名最大登录尝试次数 */
	private Integer usernameMaxAttempt;

	/** 单 IP 最大登录尝试次数 */
	private Integer ipMaxAttempt;

	/** 登录尝试计数器的过期时间（秒） */
	private Integer ttlSeconds;


}