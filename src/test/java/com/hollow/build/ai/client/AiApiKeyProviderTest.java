package com.hollow.build.ai.client;

import com.hollow.build.ai.config.AiConfigurationProperties;
import com.hollow.build.utils.RedisUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiApiKeyProvider 单测：轮询、跳过被限流的 key、全限流返回 null、markLimited 写默认 TTL。
 */
class AiApiKeyProviderTest {

    @Test
    void roundRobin_cyclesAcrossKeys() {
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        when(props.getTextApiKey()).thenReturn(List.of("a", "b", "c"));
        RedisUtil redis = mock(RedisUtil.class);
        when(redis.hasKey(anyString())).thenReturn(false);
        AiApiKeyProvider provider = new AiApiKeyProvider(props, redis);

        assertThat(provider.pickTextKey()).isEqualTo("a");
        assertThat(provider.pickTextKey()).isEqualTo("b");
        assertThat(provider.pickTextKey()).isEqualTo("c");
        assertThat(provider.pickTextKey()).isEqualTo("a");
    }

    @Test
    void skipsLimitedKeys() {
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        when(props.getTextApiKey()).thenReturn(List.of("a", "b"));
        RedisUtil redis = mock(RedisUtil.class);
        when(redis.hasKey("ai-limits-key:a")).thenReturn(true);
        when(redis.hasKey("ai-limits-key:b")).thenReturn(false);
        AiApiKeyProvider provider = new AiApiKeyProvider(props, redis);

        assertThat(provider.pickTextKey()).isEqualTo("b");
        assertThat(provider.pickTextKey()).isEqualTo("b");
    }

    @Test
    void allLimited_returnsNull() {
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        when(props.getTextApiKey()).thenReturn(List.of("a"));
        RedisUtil redis = mock(RedisUtil.class);
        when(redis.hasKey(anyString())).thenReturn(true);
        AiApiKeyProvider provider = new AiApiKeyProvider(props, redis);

        assertThat(provider.pickTextKey()).isNull();
    }

    @Test
    void markLimited_writesWithDefaultTtl() {
        AiConfigurationProperties props = mock(AiConfigurationProperties.class);
        RedisUtil redis = mock(RedisUtil.class);
        AiApiKeyProvider provider = new AiApiKeyProvider(props, redis);

        provider.markLimited("a");

        verify(redis).set("ai-limits-key:a", "1", 300L);
    }
}
