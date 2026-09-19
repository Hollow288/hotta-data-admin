package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储服务配置属性类，用于绑定连接端点及认证凭据
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.minio")
public class MinioConfigurationProperties {

	/** MinIO 服务端点地址 */
	private String endpoint;

	/** 浏览器访问 MinIO 时使用的公开端点；未配置时兼容回退到服务端点 */
	private String publicEndpoint;

	/** MinIO 访问密钥 */
	private String accessKey;

	/** MinIO 密钥 */
	private String secretKey;

	/**
	 * 获取浏览器可访问的公开端点。
	 *
	 * @return 已配置的公开端点；未配置时返回内部服务端点
	 */
	public String resolvePublicEndpoint() {
		return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
	}

}
