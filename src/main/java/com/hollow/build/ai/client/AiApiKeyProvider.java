package com.hollow.build.ai.client;

import com.hollow.build.ai.config.AiConfigurationProperties;
import com.hollow.build.utils.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 服务 API Key 的选取与限流标记。
 *
 * <p>把原先散在 {@code AiChatServiceImpl} 与 {@code OcrTranslateImageProcessor} 的逻辑收敛到一处：
 * 从配置的 key 列表里**轮询**挑一个当前未被限流的；命中 429 时把对应 key 写入 Redis 封禁一段时间。
 *
 * <p>轮询（而非每次都取第一把）是为了在多 key 下把流量摊开——否则并发时第一把会被打爆、
 * 其余空闲，直到它 429 才换下一把。
 */
@Component
public class AiApiKeyProvider {

    /** 被限流 key 在 Redis 中的前缀。 */
    private static final String LIMIT_KEY_PREFIX = "ai-limits-key:";

    /** key 被限流后的默认封禁时长（秒）。上游没给 Retry-After 时用它，取 5 分钟而非一整天，避免偶发 429 误伤一天。 */
    private static final long DEFAULT_LIMIT_TTL_SECONDS = 300;

    private final AiConfigurationProperties aiConfigurationProperties;
    private final RedisUtil redisUtil;

    /** 文本 / 图像 两个池各自的轮询游标。 */
    private final AtomicInteger textCursor = new AtomicInteger();
    private final AtomicInteger imageCursor = new AtomicInteger();

    public AiApiKeyProvider(AiConfigurationProperties aiConfigurationProperties, RedisUtil redisUtil) {
        this.aiConfigurationProperties = aiConfigurationProperties;
        this.redisUtil = redisUtil;
    }

    /** 取一个当前可用的文本/视觉模型 key，全部不可用时返回 {@code null}。 */
    public String pickTextKey() {
        return pickAvailable(aiConfigurationProperties.getTextApiKey(), textCursor);
    }

    /** 取一个当前可用的图像模型 key，全部不可用时返回 {@code null}。 */
    public String pickImageKey() {
        return pickAvailable(aiConfigurationProperties.getImageApiKey(), imageCursor);
    }

    /** 用默认 TTL 标记限流。 */
    public void markLimited(String apiKey) {
        markLimited(apiKey, DEFAULT_LIMIT_TTL_SECONDS);
    }

    /** 优先按上游响应的 {@code Retry-After}（秒）标记限流，缺省回落默认 TTL。 */
    public void markLimited(String apiKey, HttpResponse<?> response) {
        markLimited(apiKey, retryAfterOrDefault(response));
    }

    /** 把指定 key 标记为被限流，{@code ttlSeconds} 内不再被选用。 */
    public void markLimited(String apiKey, long ttlSeconds) {
        if (StringUtils.isNotBlank(apiKey)) {
            long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_LIMIT_TTL_SECONDS;
            redisUtil.set(LIMIT_KEY_PREFIX + apiKey, "1", ttl);
        }
    }

    private String pickAvailable(List<String> apiKeys, AtomicInteger cursor) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return null;
        }
        int size = apiKeys.size();
        // 从递增游标开始找，既能轮询分摊流量，也能在某把 key 被限流时继续探测后面的 key。
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        for (int i = 0; i < size; i++) {
            String apiKey = apiKeys.get((start + i) % size);
            // Redis 中存在限流标记的 key 会被临时跳过，TTL 到期后自动恢复候选资格。
            if (StringUtils.isNotBlank(apiKey) && !redisUtil.hasKey(LIMIT_KEY_PREFIX + apiKey)) {
                return apiKey;
            }
        }
        return null;
    }

    private static long retryAfterOrDefault(HttpResponse<?> response) {
        if (response != null) {
            String value = response.headers().firstValue("Retry-After").orElse("").trim();
            if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
                try {
                    long seconds = Long.parseLong(value);
                    if (seconds > 0) {
                        return seconds;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字（HTTP-date 形式）不处理，回落默认 TTL
                }
            }
        }
        return DEFAULT_LIMIT_TTL_SECONDS;
    }
}
