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

	/** MinIO 访问密钥 */
	private String accessKey;

	/** MinIO 密钥 */
	private String secretKey;

}