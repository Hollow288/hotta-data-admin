package com.hollow.build.utils;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.hollow.build.config.GoogleDriveConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveUtil {

    private final GoogleDriveConfigurationProperties properties;
    private volatile Drive driveService;

    private Drive getDriveService() throws IOException {
        if (driveService == null) {
            synchronized (this) {
                if (driveService == null) {
                    try {
                        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                        // 读取 OAuth 2.0 客户端密钥
                        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                                jsonFactory,
                                new InputStreamReader(new FileInputStream(properties.getCredentialsPath())));

                        // 构建授权流程，token 会保存到本地目录
                        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                                httpTransport, jsonFactory, clientSecrets,
                                Collections.singleton(DriveScopes.DRIVE))
                                .setDataStoreFactory(new FileDataStoreFactory(
                                        new java.io.File(properties.getTokensDir())))
                                .setAccessType("offline")
                                .build();

                        // 第一次运行会打开浏览器让你登录授权，之后自动使用保存的 refresh token
                        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                                .setPort(8888)
                                .build();
                        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                                .authorize("user");

                        driveService = new Drive.Builder(httpTransport, jsonFactory, credential)
                                .setApplicationName("hotta-data-admin")
                                .build();

                        log.info("Google Drive 服务初始化成功（OAuth 2.0）");
                    } catch (GeneralSecurityException e) {
                        throw new IOException("无法初始化 Google Drive 服务", e);
                    }
                }
            }
        }
        return driveService;
    }

    /**
     * 获取或创建指定名称的文件夹，返回文件夹 ID
     */
    public String getOrCreateFolder(String folderName) throws IOException {
        String query = String.format(
                "name = '%s' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
                folderName);
        FileList result = getDriveService().files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.getFirst().getId();
        }

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = getDriveService().files().create(folderMetadata)
                .setFields("id")
                .execute();

        log.info("创建 Google Drive 文件夹: {} ({})", folderName, folder.getId());
        return folder.getId();
    }

    /**
     * 上传文件到指定文件夹
     */
    public String uploadFile(Path filePath, String folderId) throws IOException {
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        File fileMetadata = new File();
        fileMetadata.setName(filePath.getFileName().toString());
        fileMetadata.setParents(Collections.singletonList(folderId));

        FileContent mediaContent = new FileContent(mimeType, filePath.toFile());

        File uploadedFile = getDriveService().files().create(fileMetadata, mediaContent)
                .setFields("id, name, size")
                .execute();

        log.info("文件上传成功: {} (id={}, size={})", uploadedFile.getName(), uploadedFile.getId(), uploadedFile.getSize());
        return uploadedFile.getId();
    }

    /**
     * 检查文件是否已存在于指定文件夹中（按文件名判断）
     */
    public boolean fileExists(String fileName, String folderId) throws IOException {
        String query = String.format(
                "name = '%s' and '%s' in parents and trashed = false",
                fileName, folderId);
        FileList result = getDriveService().files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute();

        return result.getFiles() != null && !result.getFiles().isEmpty();
    }
}
