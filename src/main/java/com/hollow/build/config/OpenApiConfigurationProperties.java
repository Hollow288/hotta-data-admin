package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAPI 文档配置属性类，用于绑定 API 文档的标题、版本等信息
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.open-api")
public class OpenApiConfigurationProperties {

	/** 是否启用 OpenAPI 文档 */
	private boolean enabled;

	/** API 文档标题 */
	private String title;

	/** API 版本号 */
	private String apiVersion;

	/** API 文档描述信息 */
	private String description;



}