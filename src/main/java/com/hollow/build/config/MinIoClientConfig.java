package com.hollow.build.config;

import io.minio.MinioClient;
import jakarta.servlet.MultipartConfigElement;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * MinIO 客户端配置类，负责创建 MinIO 客户端实例及文件上传大小限制配置
 */
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
    @Primary
    public MinioClient minioClient() {
        return buildClient(minioConfigurationProperties.getEndpoint());
    }

    /**
     * 注入用于生成浏览器预签名 URL 的 MinIO 客户端。
     *
     * @return 使用公开端点的 minioClient
     */
    @Bean("publicMinioClient")
    public MinioClient publicMinioClient() {
        return buildClient(minioConfigurationProperties.resolvePublicEndpoint());
    }

    private MinioClient buildClient(String endpoint) {
        return MinioClient.builder()
                .endpoint(endpoint)
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
