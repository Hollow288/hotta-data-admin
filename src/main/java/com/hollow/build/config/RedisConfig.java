package com.hollow.build.config;

import com.hollow.build.utils.FastJsonRedisSerializer;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;

/**
 * Redis 配置类，定义 RedisTemplate 序列化方式及 Redisson 客户端
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 RedisTemplate 实例，配置 Key 和 Value 的序列化策略
     *
     * @param factory Redis 连接工厂
     * @return 配置完成的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);


        FastJsonRedisSerializer<Object> serializer = new FastJsonRedisSerializer<>(Object.class);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        // Hash的key也采用StringRedisSerializer的序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();


        return template;
    }


    /**
     * 创建 Redisson 客户端实例，用于分布式锁等高级 Redis 功能
     *
     * @param redisProperties Redis 连接属性配置
     * @return Redisson 客户端实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(final RedisProperties redisProperties) {
        String redisAddress = String.format("redis://%s:%d", redisProperties.getHost(), redisProperties.getPort());
        final var configuration = new Config();
        configuration.useSingleServer().setPassword(redisProperties.getPassword()).setAddress(redisAddress);
        return Redisson.create(configuration);
    }




}
