package com.hollow.build.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 服务配置属性类，用于绑定 AI 文本和图像生成相关的配置项
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.ai")
public class AiConfigurationProperties {

    /** 文本 AI 服务的 API Key 列表 */
    private List<String> textApiKey;

    /** 文本生成的默认提示词 */
    private String textDefaultPrompt;

    /** 文本生成使用的模型名称 */
    private String textModel;

    /** 文本 AI 服务的请求地址，通常是 OpenAI 兼容的完整 /v1/chat/completions 地址 */
    private String textUri;

    /** 图像 AI 服务的 API Key 列表 */
    private List<String> imageApiKey;

    /** 图像生成的默认提示词 */
    private String imageDefaultPrompt;

    /** 图像生成使用的模型名称 */
    private String imageModel;

    /** 图像 AI 服务的请求地址前缀，Gemini 客户端会在后面拼接 model + ":generateContent" */
    private String imageUri;

    /** 代理服务器地址 */
    private String proxyAddress;

    /** 代理服务器端口 */
    private Integer proxyPort;

    /** 是否启用代理 */
    private boolean proxyEnabled;


}
