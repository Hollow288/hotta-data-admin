package com.hollow.build.ai.client;

import com.hollow.build.ai.config.AiConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * AI 调用统一使用的 {@link HttpClient}。
 *
 * <p>原先 {@code AiChatServiceImpl}、agent 客户端、{@code OcrTranslateImageProcessor}
 * 各自 new 一个结构完全相同的客户端（40s 连接超时 / HTTP_2 / 跟随重定向 / 按配置挂代理）。
 * 这里收敛成唯一一个 bean，统一由 {@code OpenAiChatClient} / {@code OpenAiImageClient}
 * （以及保留的 {@code GeminiImageClient}）注入，避免重复创建。
 *
 * <p>注意：{@code OcrRemoteClient} 打的是外部 OCR 服务、与 AI 无关，仍用它自己的客户端，不在此列。
 */
@Configuration
public class AiHttpClientConfig {

    @Bean
    public HttpClient aiHttpClient(AiConfigurationProperties aiConfigurationProperties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(40))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);

        if (aiConfigurationProperties.isProxyEnabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    aiConfigurationProperties.getProxyAddress(),
                    aiConfigurationProperties.getProxyPort()
            )));
        }

        return builder.build();
    }
}
