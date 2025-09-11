package com.hollow.build.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        // 虚拟线程池
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
