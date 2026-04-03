package com.hollow.build.service;

import com.hollow.build.repository.mysql.UserMapper;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import lombok.RequiredArgsConstructor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static java.time.Duration.ofSeconds;


/**
 * 限流服务类，基于Bucket4j和Redis实现API请求速率限制
 */
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;

    /**
     * 根据标识获取对应的限流桶，使用Redis作为分布式存储
     *
     * @param id 限流标识（API Key）
     * @return 对应的限流桶实例
     */
    public Bucket getBucket(String id) {

        RedissonBasedProxyManager<String> proxyManager = Bucket4jRedisson.casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ofSeconds(7200)))
                .keyMapper(Mapper.STRING)
                .build();

//        return proxyManager.getProxy("rate-limit-" + id, () -> getConfigurationByUserId(Long.valueOf(id)));
        return proxyManager.getProxy("rate-limit-" + id, () -> getConfigurationByApiKey(id));
    }

    /**
     * 根据API Key获取限流配置，从数据库查询该Key对应的每小时限流次数
     *
     * @param apiKey API密钥
     * @return 限流桶配置
     * @throws AuthenticationCredentialsNotFoundException 当API Key无效或无限流配置时抛出
     */
    public BucketConfiguration getConfigurationByApiKey(String apiKey) {
        Integer limitPerHour = userMapper.getLimitPerHourByApiKey(apiKey);

        if (limitPerHour == null || limitPerHour <= 0) {
            // API Key 无效或没有限流配置
            throw new AuthenticationCredentialsNotFoundException("Invalid or missing API Key");
        }

        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(limitPerHour).refillIntervally(limitPerHour, Duration.ofHours(1)))
                .build();
    }

    /**
     * 根据用户ID获取限流配置，从数据库查询该用户对应的每小时限流次数
     *
     * @param userId 用户ID
     * @return 限流桶配置
     */
    public BucketConfiguration getConfigurationByUserId(Long userId) {
        Integer limitPerHour = userMapper.getLimitPerHourByUserId(userId);

        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(limitPerHour).refillIntervally(limitPerHour, Duration.ofHours(1)))
                .build();
    }
}
