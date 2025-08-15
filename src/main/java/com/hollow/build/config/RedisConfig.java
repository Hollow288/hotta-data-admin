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

@Configuration
public class RedisConfig {

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


    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(final RedisProperties redisProperties) {
        String redisAddress = String.format("redis://%s:%d", redisProperties.getHost(), redisProperties.getPort());
        final var configuration = new Config();
        configuration.useSingleServer().setPassword(redisProperties.getPassword()).setAddress(redisAddress);
        return Redisson.create(configuration);
    }




}
