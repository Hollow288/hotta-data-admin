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


@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;

    public Bucket getBucket(String id) {

        RedissonBasedProxyManager<String> proxyManager = Bucket4jRedisson.casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ofSeconds(7200)))
                .keyMapper(Mapper.STRING)
                .build();

//        return proxyManager.getProxy("rate-limit-" + id, () -> getConfigurationByUserId(Long.valueOf(id)));
        return proxyManager.getProxy("rate-limit-" + id, () -> getConfigurationByApiKey(id));
    }

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

    public BucketConfiguration getConfigurationByUserId(Long userId) {
        Integer limitPerHour = userMapper.getLimitPerHourByUserId(userId);

        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(limitPerHour).refillIntervally(limitPerHour, Duration.ofHours(1)))
                .build();
    }
}
