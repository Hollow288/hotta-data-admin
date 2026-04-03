package com.hollow.build.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "com.hollow.google-drive")
public class GoogleDriveConfigurationProperties {

    /**
     * OAuth 2.0 客户端密钥 JSON 文件路径
     */
    private String credentialsPath;

    /**
     * OAuth token 存储目录（保存 refresh token，授权一次后自动复用）
     */
    private String tokensDir = "./config/tokens";

    /**
     * 要扫描的本地视频目录
     */
    private String watchDir;

    /**
     * Google Drive 中目标文件夹名称
     */
    private String targetFolder = "video";

    /**
     * 上传成功后是否删除本地文件
     */
    private boolean deleteAfterUpload = true;

    /**
     * 定时任务 cron 表达式，默认每10分钟执行一次
     */
    private String cron = "0 */5 * * * ?";
}
