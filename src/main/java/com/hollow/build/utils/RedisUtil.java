package com.hollow.build.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类，封装常用的 Key、String、Set、Hash 与 List 读写能力。
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== Key 操作 ====================

    /**
     * 判断指定键是否存在。
     *
     * @param key Redis 键
     * @return 键存在返回 true，否则返回 false
     */
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 按模式获取键集合。
     *
     * @param pattern Redis 键模式，例如 "ocr:result:*"
     * @return 匹配到的键集合，未命中时返回空集合
     */
    public Set<String> keys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys == null ? Set.of() : keys;
    }

    /**
     * 删除指定键。
     *
     * @param key Redis 键
     */
    public void removeKey(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 为指定键设置过期时间。
     *
     * @param key Redis 键
     * @param timeSeconds 过期时间，单位为秒
     * @return 设置成功返回 true，否则返回 false
     */
    public boolean expire(String key, long timeSeconds) {
        return redisTemplate.expire(key, timeSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取指定键的剩余生存时间。
     *
     * @param key Redis 键
     * @return 键剩余过期时间，单位为秒
     */
    public long getTime(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 移除指定键的过期时间，使其转为持久化键。
     *
     * @param key Redis 键
     * @return 移除成功返回 true，否则返回 false
     */
    public boolean persist(String key) {
        return Boolean.TRUE.equals(redisTemplate.boundValueOps(key).persist());
    }

    // ==================== String ====================

    /**
     * 写入字符串值，不设置过期时间。
     *
     * @param key Redis 键
     * @param value 要写入的值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入字符串值，并在需要时设置过期时间。
     *
     * @param key Redis 键
     * @param value 要写入的值
     * @param timeSeconds 过期时间，单位为秒
     */
    public void set(String key, Object value, long timeSeconds) {
        if (timeSeconds > 0) {
            redisTemplate.opsForValue().set(key, value, timeSeconds, TimeUnit.SECONDS);
        } else {
            set(key, value);
        }
    }

    /**
     * 读取字符串值。
     *
     * @param key Redis 键
     * @return 对应的值，不存在时返回 null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 对字符串数值执行整型自增。
     *
     * @param key Redis 键
     * @param delta 增量值
     * @return 自增后的结果
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 对字符串数值执行浮点自增。
     *
     * @param key Redis 键
     * @param delta 增量值
     * @return 自增后的结果
     */
    public Double increment(String key, double delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // ==================== Set ====================

    /**
     * 向集合中添加成员。
     *
     * @param key Redis 键
     * @param value 集合成员
     */
    public void addToSet(String key, Object value) {
        redisTemplate.opsForSet().add(key, value);
    }

    /**
     * 获取集合中的全部成员。
     *
     * @param key Redis 键
     * @return 集合成员列表
     */
    public Set<Object> getSetMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断指定值是否属于集合成员。
     *
     * @param key Redis 键
     * @param value 待判断的成员值
     * @return 存在返回 true，否则返回 false
     */
    public boolean isMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * 获取集合元素数量。
     *
     * @param key Redis 键
     * @return 集合大小
     */
    public long getSetSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    /**
     * 从集合中移除一个或多个成员。
     *
     * @param key Redis 键
     * @param values 待移除的成员列表
     */
    public void removeSetMembers(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }

    // ==================== Hash ====================

    /**
     * 批量写入哈希字段。
     *
     * @param key Redis 键
     * @param map 字段与值的映射
     */
    public void putAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 获取哈希中的全部字段和值。
     *
     * @param key Redis 键
     * @return 哈希字段映射
     */
    public Map<Object, Object> getHashEntries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 判断哈希中是否存在指定字段。
     *
     * @param key Redis 键
     * @param hashKey 哈希字段名
     * @return 存在返回 true，否则返回 false
     */
    public boolean hasHashKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * 获取哈希指定字段的值。
     *
     * @param key Redis 键
     * @param hashKey 哈希字段名
     * @return 字段对应的值，不存在时返回 null
     */
    public Object getHashValue(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 删除哈希中的一个或多个字段。
     *
     * @param key Redis 键
     * @param hashKeys 待删除的字段名列表
     * @return 实际删除的字段数量
     */
    public Long deleteHashKeys(String key, String... hashKeys) {
        return redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    /**
     * 对哈希字段执行整型自增。
     *
     * @param key Redis 键
     * @param hashKey 哈希字段名
     * @param delta 增量值
     * @return 自增后的结果
     */
    public Long incrementHash(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    /**
     * 对哈希字段执行浮点自增。
     *
     * @param key Redis 键
     * @param hashKey 哈希字段名
     * @param delta 增量值
     * @return 自增后的结果
     */
    public Double incrementHash(String key, String hashKey, double delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    /**
     * 获取哈希中的全部字段名。
     *
     * @param key Redis 键
     * @return 哈希字段名集合
     */
    public Set<Object> getHashKeys(String key) {
        return redisTemplate.opsForHash().keys(key);
    }

    /**
     * 获取哈希字段数量。
     *
     * @param key Redis 键
     * @return 哈希字段总数
     */
    public long getHashSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    // ==================== List ====================

    /**
     * 从列表左侧压入元素。
     *
     * @param key Redis 键
     * @param value 待压入的值
     */
    public void leftPush(String key, Object value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 从列表右侧压入元素。
     *
     * @param key Redis 键
     * @param value 待压入的值
     */
    public void rightPush(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 获取列表指定区间内的元素。
     *
     * @param key Redis 键
     * @param start 起始下标
     * @param end 结束下标
     * @return 区间内的元素列表
     */
    public List<Object> rangeList(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 获取列表长度。
     *
     * @param key Redis 键
     * @return 列表元素总数
     */
    public long listSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    /**
     * 从列表左侧弹出一个元素。
     *
     * @param key Redis 键
     * @return 弹出的元素，不存在时返回 null
     */
    public Object leftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从列表右侧弹出一个元素。
     *
     * @param key Redis 键
     * @return 弹出的元素，不存在时返回 null
     */
    public Object rightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }
}
