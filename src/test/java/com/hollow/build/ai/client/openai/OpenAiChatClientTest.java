package com.hollow.build.ai.client.openai;

import com.hollow.build.ai.client.AiApiKeyProvider;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAiChatClient 的纯逻辑单测：用 Mockito 喂各种上游响应，验证错误解析、内容提取、429 限流标记、无 key 兜底。
 * 不起 Spring、不走网络。
 */
class OpenAiChatClientTest {

    private HttpClient httpClient;
    private AiApiKeyProvider keys;
    private OpenAiChatClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        when(props.getTextModel()).thenReturn("test-model");
        when(props.getTextUri()).thenReturn("http://localhost/v1/chat/completions");
        keys = mock(AiApiKeyProvider.class);
        when(keys.pickTextKey()).thenReturn("k1");
        client = new OpenAiChatClient(httpClient, props, keys);
    }

    private ChatRequest req() {
        return ChatRequest.builder().messages(List.of(ChatMessages.user("hi"))).build();
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);
    }

    @Test
    void success_extractsStringContent() throws Exception {
        stubResponse(200, "{\"choices\":[{\"message\":{\"content\":\"hello\"},\"finish_reason\":\"stop\"}]," +
                "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}");

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isFalse();
        assertThat(r.content()).isEqualTo("hello");
        assertThat(r.totalTokens()).isEqualTo(5);
        assertThat(r.finishReason()).isEqualTo("stop");
    }

    @Test
    void success_joinsArrayContentParts() throws Exception {
        stubResponse(200, "{\"choices\":[{\"message\":{\"content\":" +
                "[{\"type\":\"text\",\"text\":\"foo\"},{\"type\":\"text\",\"text\":\"bar\"}]}}]}");

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isFalse();
        assertThat(r.content()).isEqualTo("foobar");
    }

    @Test
    void errorArray_returnsMessageAndMarksLimitedOn429() throws Exception {
        stubResponse(200, "[{\"error\":{\"code\":429,\"message\":\"rate limited\"}}]");

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isTrue();
        assertThat(r.errorMessage()).isEqualTo("rate limited");
        verify(keys).markLimited(eq("k1"), any(HttpResponse.class));
    }

    @Test
    void errorObject_returnsMessage() throws Exception {
        stubResponse(400, "{\"error\":{\"message\":\"bad request\"}}");

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isTrue();
        assertThat(r.errorMessage()).isEqualTo("bad request");
    }

    @Test
    void noChoices_isError() throws Exception {
        stubResponse(200, "{\"choices\":[]}");

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isTrue();
    }

    @Test
    void noAvailableKey_failsWithoutHttpCall() throws Exception {
        when(keys.pickTextKey()).thenReturn(null);

        ChatResult r = client.complete(req());

        assertThat(r.isError()).isTrue();
        assertThat(r.errorMessage()).contains("API Key");
        verify(httpClient, never()).send(any(), any());
    }
}
