package com.hollow.build.ai.client.gemini;

import com.hollow.build.ai.client.AiApiKeyProvider;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GeminiImageClient 的纯逻辑单测：验证 inlineData 提取、finishReason 非 STOP、429 限流标记、无 key 兜底。
 */
class GeminiImageClientTest {

    private HttpClient httpClient;
    private AiApiKeyProvider keys;
    private GeminiImageClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        when(props.getImageModel()).thenReturn("img-model");
        when(props.getImageUri()).thenReturn("http://localhost/v1beta/models/");
        keys = mock(AiApiKeyProvider.class);
        when(keys.pickImageKey()).thenReturn("ik1");
        client = new GeminiImageClient(httpClient, props, keys);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);
    }

    @Test
    void success_extractsInlineData() throws Exception {
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":" +
                "[{\"inlineData\":{\"data\":\"BASE64\",\"mimeType\":\"image/png\"}}]}}]}");

        GeminiImageResult r = client.generateImage("draw a cat", null, null);

        assertThat(r.isError()).isFalse();
        assertThat(r.data()).isEqualTo("BASE64");
        assertThat(r.mimeType()).isEqualTo("image/png");
    }

    @Test
    void nonStopFinishReason_isError() throws Exception {
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");

        GeminiImageResult r = client.generateImage("x", null, null);

        assertThat(r.isError()).isTrue();
        assertThat(r.finishReason()).isEqualTo("SAFETY");
    }

    @Test
    void errorArray_marksLimitedOn429() throws Exception {
        stubResponse(429, "[{\"error\":{\"code\":429,\"message\":\"quota\"}}]");

        GeminiImageResult r = client.generateImage("x", null, null);

        assertThat(r.isError()).isTrue();
        assertThat(r.errorMessage()).isEqualTo("quota");
        verify(keys).markLimited(eq("ik1"), any(HttpResponse.class));
    }

    @Test
    void noKey_failsWithoutHttpCall() throws Exception {
        when(keys.pickImageKey()).thenReturn(null);

        GeminiImageResult r = client.generateImage("x", null, null);

        assertThat(r.isError()).isTrue();
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void picksImagePart_whenTextPartComesFirst() throws Exception {
        // Gemini 可能先返回一段文字 part、再返回图 part，应遍历命中 inlineData 而非直接取 parts[0]。
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":" +
                "[{\"text\":\"here is your image\"}," +
                "{\"inlineData\":{\"data\":\"IMG\",\"mimeType\":\"image/png\"}}]}}]}");

        GeminiImageResult r = client.generateImage("draw", null, null);

        assertThat(r.isError()).isFalse();
        assertThat(r.data()).isEqualTo("IMG");
        assertThat(r.mimeType()).isEqualTo("image/png");
    }

    @Test
    void missingParts_isErrorNotNpe() throws Exception {
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{}}]}");

        GeminiImageResult r = client.generateImage("draw", null, null);

        assertThat(r.isError()).isTrue();
    }
}
