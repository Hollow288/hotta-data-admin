package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.ai")
public class AiConfigurationProperties {

    private List<String> textApiKey;

    private String textDefaultPrompt;

    private String textModel;

    private String textUri;


    private List<String> imageApiKey;

    private String imageDefaultPrompt;

    private String imageModel;

    private String imageUri;

    private String proxyAddress;

    private Integer proxyPort;

    private boolean proxyEnabled;


}