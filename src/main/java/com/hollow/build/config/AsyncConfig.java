package com.hollow.build.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步任务配置类，启用虚拟线程池并配置异步支持
 */
@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    /**
     * 创建基于虚拟线程的任务执行器
     *
     * @return 虚拟线程任务执行器
     */
    @Bean
    public Executor taskExecutor() {
        // 虚拟线程池
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 配置 Spring MVC 异步请求支持，设置执行器和超时时间
     *
     * @param configurer 异步支持配置器
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 使用 TaskExecutorAdapter 包装一下，因为接口类型不匹配
        configurer.setTaskExecutor(new TaskExecutorAdapter(taskExecutor()));
        configurer.setDefaultTimeout(60_000); // 设置超时
    }
}
