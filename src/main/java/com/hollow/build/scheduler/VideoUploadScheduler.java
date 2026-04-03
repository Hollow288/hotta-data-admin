package com.hollow.build.scheduler;

import com.hollow.build.config.GoogleDriveConfigurationProperties;
import com.hollow.build.utils.GoogleDriveUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 视频上传定时任务调度器。
 * <p>定期扫描指定目录下的视频文件，自动上传至 Google Drive，
 * 并根据配置决定上传后是否删除本地文件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoUploadScheduler {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".mpeg", ".mpg", ".3gp"
    );

    private final GoogleDriveUtil googleDriveUtil;
    private final GoogleDriveConfigurationProperties properties;

    /**
     * 扫描监控目录并上传视频文件至 Google Drive。
     * <p>按 cron 表达式定时执行，默认每 5 分钟一次。
     * 若文件已存在于 Google Drive 则跳过上传。</p>
     */
    @Scheduled(cron = "${com.hollow.google-drive.cron:0 */5 * * * ?}")
    public void scanAndUploadVideos() {
        Path watchDir = Paths.get(properties.getWatchDir());
        if (!Files.exists(watchDir) || !Files.isDirectory(watchDir)) {
            log.warn("监控目录不存在或不是目录: {}", watchDir);
            return;
        }

        log.info("开始扫描视频目录: {}", watchDir);

        try {
            String folderId = googleDriveUtil.getOrCreateFolder(properties.getTargetFolder());

            try (Stream<Path> stream = Files.walk(watchDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(file -> {
                            String name = file.getFileName().toString().toLowerCase();
                            return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
                        })
                        .forEach(file -> {
                            try {
                                if (googleDriveUtil.fileExists(file.getFileName().toString(), folderId)) {
                                    log.info("文件已存在于 Google Drive，跳过: {}", file.getFileName());
                                    if (properties.isDeleteAfterUpload()) {
                                        Files.delete(file);
                                        log.info("已删除本地已上传文件: {}", file.getFileName());
                                    }
                                    return;
                                }

                                googleDriveUtil.uploadFile(file, folderId);

                                if (properties.isDeleteAfterUpload()) {
                                    Files.delete(file);
                                    log.info("已删除本地文件: {}", file.getFileName());
                                }
                            } catch (IOException e) {
                                log.error("上传文件失败: {}", file.getFileName(), e);
                            }
                        });
            }

            log.info("视频扫描上传任务完成");
        } catch (IOException e) {
            log.error("视频上传定时任务执行失败", e);
        }
    }
}
