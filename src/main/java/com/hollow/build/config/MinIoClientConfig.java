package com.hollow.build.config;

import io.minio.MinioClient;
import jakarta.servlet.MultipartConfigElement;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Data
@Component
@RequiredArgsConstructor
public class MinIoClientConfig {

    private final MinioConfigurationProperties minioConfigurationProperties;

    /**
     * 注入minio 客户端
     *
     * @return minioClient
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioConfigurationProperties.getEndpoint())
                .credentials(minioConfigurationProperties.getAccessKey(), minioConfigurationProperties.getSecretKey())
                .build();
    }

    /**
     * 控制上传文件的大小
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // 单个数据大小
        factory.setMaxFileSize(DataSize.parse("1024MB")); // KB,MB
        /// 总上传数据大小
        factory.setMaxRequestSize(DataSize.parse("10240MB"));
        return factory.createMultipartConfig();
    }

}