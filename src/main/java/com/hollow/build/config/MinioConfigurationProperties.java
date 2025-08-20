package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.minio")
public class MinioConfigurationProperties {

	private String endpoint;

	private String accessKey;

	private String secretKey;

}