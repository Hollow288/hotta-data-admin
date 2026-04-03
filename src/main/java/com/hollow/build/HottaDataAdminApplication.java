package com.hollow.build;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@EnableCaching
@EnableScheduling
@SpringBootApplication
@EnableWebSecurity
@EnableMethodSecurity
/**
 * Spring Boot 应用启动入口，负责启用缓存、定时任务和方法级安全能力。
 */
public class HottaDataAdminApplication {

    /**
     * 启动 Hotta Data Admin 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HottaDataAdminApplication.class, args);
    }

}
