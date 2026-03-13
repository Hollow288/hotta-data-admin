package com.hollow.build.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Bean
    public Executor taskExecutor() {
        // 虚拟线程池
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 使用 TaskExecutorAdapter 包装一下，因为接口类型不匹配
        configurer.setTaskExecutor(new TaskExecutorAdapter(taskExecutor()));
        configurer.setDefaultTimeout(60_000); // 设置超时
    }
}
