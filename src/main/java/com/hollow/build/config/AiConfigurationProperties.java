package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.ai")
public class AiConfigurationProperties {

    private String apiKey;

    private String defaultPrompt;

    private String model;

    private String uri;

}