package com.hollow.build.utils;

import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 基于 FastJSON 的 Redis 序列化器
 * <p>
 * 使用 FastJSON2 实现 Redis 值的序列化与反序列化，
 * 替代默认的 JDK 序列化方式，提升可读性和性能。
 * </p>
 *
 * @param <T> 序列化对象的类型
 */
public class FastJsonRedisSerializer<T> implements RedisSerializer<T> {

    private final Class<T> clazz;

    /**
     * 构造方法
     *
     * @param clazz 反序列化时的目标类型
     */
    public FastJsonRedisSerializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * 将对象序列化为 JSON 字节数组
     *
     * @param t 要序列化的对象
     * @return JSON 格式的字节数组；对象为 null 时返回空字节数组
     * @throws SerializationException 序列化异常
     */
    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        return JSON.toJSONBytes(t);
    }

    /**
     * 将 JSON 字节数组反序列化为对象
     *
     * @param bytes JSON 格式的字节数组
     * @return 反序列化后的对象；字节数组为 null 或空时返回 null
     * @throws SerializationException 反序列化异常
     */
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return JSON.parseObject(bytes, clazz);
    }
}