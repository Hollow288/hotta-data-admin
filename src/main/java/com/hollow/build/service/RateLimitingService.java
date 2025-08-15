package com.hollow.build.service;

import com.hollow.build.mapper.UserMapper;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import lombok.RequiredArgsConstructor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static java.time.Duration.ofSeconds;


@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;

    public Bucket getBucket(Long userId) {

        RedissonBasedProxyManager<String> proxyManager = Bucket4jRedisson.casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ofSeconds(7200)))
                .keyMapper(Mapper.STRING)
                .build();

        String key = userId.toString();

        return proxyManager.getProxy("rate-limit-" + key, () -> getConfiguration(userId));
    }


    public BucketConfiguration getConfiguration(Long userId) {
        int limitPerHour = userMapper.getLimitPerHourByUserId(userId);
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(limitPerHour).refillIntervally(limitPerHour, Duration.ofHours(1)))
                .build();
    }
}
