package com.hollow.build.scheduler;

import com.hollow.build.config.GoogleDriveConfigurationProperties;
import com.hollow.build.utils.GoogleDriveUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoUploadScheduler {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".mpeg", ".mpg", ".3gp"
    );

    private final GoogleDriveUtil googleDriveUtil;
    private final GoogleDriveConfigurationProperties properties;

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

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(watchDir)) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }

                    String fileName = file.getFileName().toString().toLowerCase();
                    boolean isVideo = VIDEO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
                    if (!isVideo) {
                        continue;
                    }

                    try {
                        if (googleDriveUtil.fileExists(file.getFileName().toString(), folderId)) {
                            log.info("文件已存在于 Google Drive，跳过: {}", file.getFileName());
                            if (properties.isDeleteAfterUpload()) {
                                Files.delete(file);
                                log.info("已删除本地已上传文件: {}", file.getFileName());
                            }
                            continue;
                        }

                        googleDriveUtil.uploadFile(file, folderId);

                        if (properties.isDeleteAfterUpload()) {
                            Files.delete(file);
                            log.info("已删除本地文件: {}", file.getFileName());
                        }
                    } catch (IOException e) {
                        log.error("上传文件失败: {}", file.getFileName(), e);
                    }
                }
            }

            log.info("视频扫描上传任务完成");
        } catch (IOException e) {
            log.error("视频上传定时任务执行失败", e);
        }
    }
}
