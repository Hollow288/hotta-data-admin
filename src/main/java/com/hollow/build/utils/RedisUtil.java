package com.hollow.build.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== Key 操作 ====================

    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public void removeKey(String key) {
        redisTemplate.delete(key);
    }

    public boolean expire(String key, long timeSeconds) {
        return redisTemplate.expire(key, timeSeconds, TimeUnit.SECONDS);
    }

    public long getTime(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public boolean persist(String key) {
        return Boolean.TRUE.equals(redisTemplate.boundValueOps(key).persist());
    }

    // ==================== String ====================

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeSeconds) {
        if (timeSeconds > 0) {
            redisTemplate.opsForValue().set(key, value, timeSeconds, TimeUnit.SECONDS);
        } else {
            set(key, value);
        }
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Double increment(String key, double delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // ==================== Set ====================

    public void addToSet(String key, Object value) {
        redisTemplate.opsForSet().add(key, value);
    }

    public Set<Object> getSetMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public boolean isMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    public long getSetSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    public void removeSetMembers(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }

    // ==================== Hash ====================

    public void putAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Map<Object, Object> getHashEntries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public boolean hasHashKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    public Object getHashValue(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public Long deleteHashKeys(String key, String... hashKeys) {
        return redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    public Long incrementHash(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    public Double incrementHash(String key, String hashKey, double delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    public Set<Object> getHashKeys(String key) {
        return redisTemplate.opsForHash().keys(key);
    }

    public long getHashSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    // ==================== List ====================

    public void leftPush(String key, Object value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    public void rightPush(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    public List<Object> rangeList(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public long listSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    public Object leftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Object rightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }
}
