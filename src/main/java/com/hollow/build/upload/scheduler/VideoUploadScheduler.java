package com.hollow.build.upload.scheduler;

import com.hollow.build.upload.config.GoogleDriveConfigurationProperties;
import com.hollow.build.upload.util.GoogleDriveUtil;
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
import java.util.concurrent.TimeUnit;

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
                                if (!isFileStable(file)) {
                                    log.info("文件仍在写入中，跳过: {}", file.getFileName());
                                    return;
                                }

                                if (googleDriveUtil.fileExists(file.getFileName().toString(), folderId)) {
                                    log.info("文件已存在于 Google Drive，跳过: {}", file.getFileName());
                                    return;
                                }

                                googleDriveUtil.uploadFile(file, folderId);
                            } catch (IOException | InterruptedException e) {
                                log.error("上传文件失败: {}", file.getFileName(), e);
                            }
                        });
            }

            log.info("视频扫描上传任务完成");
        } catch (IOException e) {
            log.error("视频上传定时任务执行失败", e);
        }
    }

    /**
     * 检查文件大小是否稳定，用于判断 qBittorrent 是否已完成下载。
     * 间隔 3 秒采样两次，若大小一致且不为零则认为文件已写入完毕。
     */
    private boolean isFileStable(Path file) throws IOException, InterruptedException {
        long size1 = Files.size(file);
        TimeUnit.SECONDS.sleep(3);
        long size2 = Files.size(file);
        return size1 == size2 && size1 > 0;
    }
}
