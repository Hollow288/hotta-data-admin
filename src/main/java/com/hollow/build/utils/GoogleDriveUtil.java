package com.hollow.build.utils;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.hollow.build.config.GoogleDriveConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

    /**
     * 获取 Google Drive 服务实例（懒加载，线程安全）
     * 第一次调用时会触发 OAuth 2.0 授权流程
     *
     * @return Drive 服务实例
     * @throws IOException 初始化失败时抛出
     */
    private Drive getDriveService() throws IOException {
        if (driveService == null) {
            synchronized (this) {
                if (driveService == null) {
                    try {
                        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                                jsonFactory,
                                new InputStreamReader(new FileInputStream(properties.getCredentialsPath())));

                        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                                httpTransport, jsonFactory, clientSecrets,
                                Collections.singleton(DriveScopes.DRIVE))
                                .setDataStoreFactory(new FileDataStoreFactory(
                                        new java.io.File(properties.getTokensDir())))
                                .setAccessType("offline")
                                .build();

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

    // ==================== 文件夹操作 ====================

    /**
     * 获取或创建指定名称的文件夹，返回文件夹 ID
     * 如果同名文件夹已存在，直接返回已有文件夹的 ID；否则创建新文件夹
     *
     * @param folderName 文件夹名称
     * @return 文件夹 ID
     * @throws IOException 操作失败时抛出
     */
    public String getOrCreateFolder(String folderName) throws IOException {
        return getOrCreateFolder(folderName, null);
    }

    /**
     * 在指定父文件夹下获取或创建文件夹
     *
     * @param folderName     文件夹名称
     * @param parentFolderId 父文件夹 ID，传 null 则在根目录创建
     * @return 文件夹 ID
     * @throws IOException 操作失败时抛出
     */
    public String getOrCreateFolder(String folderName, String parentFolderId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append(String.format("name = '%s' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", folderName));
        if (parentFolderId != null) {
            query.append(String.format(" and '%s' in parents", parentFolderId));
        }

        FileList result = getDriveService().files().list()
                .setQ(query.toString())
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
        if (parentFolderId != null) {
            folderMetadata.setParents(Collections.singletonList(parentFolderId));
        }

        File folder = getDriveService().files().create(folderMetadata)
                .setFields("id")
                .execute();

        log.info("创建 Google Drive 文件夹: {} ({})", folderName, folder.getId());
        return folder.getId();
    }

    // ==================== 上传 ====================

    /**
     * 上传本地文件到指定文件夹
     *
     * @param filePath 本地文件路径
     * @param folderId 目标文件夹 ID
     * @return 上传后的文件 ID
     * @throws IOException 上传失败时抛出
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
     * 上传字节数组到指定文件夹
     *
     * @param data     文件内容的字节数组
     * @param fileName 文件名（含扩展名，如 "report.pdf"）
     * @param mimeType MIME 类型（如 "application/pdf"），传 null 则使用 "application/octet-stream"
     * @param folderId 目标文件夹 ID
     * @return 上传后的文件 ID
     * @throws IOException 上传失败时抛出
     */
    public String uploadFile(byte[] data, String fileName, String mimeType, String folderId) throws IOException {
        return uploadFile(new ByteArrayInputStream(data), fileName, mimeType, folderId);
    }

    /**
     * 上传输入流到指定文件夹
     *
     * @param inputStream 文件内容的输入流
     * @param fileName    文件名（含扩展名，如 "video.mp4"）
     * @param mimeType    MIME 类型（如 "video/mp4"），传 null 则使用 "application/octet-stream"
     * @param folderId    目标文件夹 ID
     * @return 上传后的文件 ID
     * @throws IOException 上传失败时抛出
     */
    public String uploadFile(InputStream inputStream, String fileName, String mimeType, String folderId) throws IOException {
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(folderId));

        AbstractInputStreamContent mediaContent = new InputStreamContent(mimeType, inputStream);

        File uploadedFile = getDriveService().files().create(fileMetadata, mediaContent)
                .setFields("id, name, size")
                .execute();

        log.info("文件上传成功: {} (id={}, size={})", uploadedFile.getName(), uploadedFile.getId(), uploadedFile.getSize());
        return uploadedFile.getId();
    }

    // ==================== 下载 ====================

    /**
     * 下载文件到本地指定路径
     *
     * @param fileId   Google Drive 文件 ID
     * @param destPath 本地目标路径（如 /tmp/download.mp4）
     * @throws IOException 下载失败时抛出
     */
    public void downloadFile(String fileId, Path destPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(destPath)) {
            getDriveService().files().get(fileId).executeMediaAndDownloadTo(out);
        }
        log.info("文件下载成功: {} -> {}", fileId, destPath);
    }

    /**
     * 获取文件内容的输入流（适用于不需要保存到本地的场景）
     *
     * @param fileId Google Drive 文件 ID
     * @return 文件内容的输入流，调用方负责关闭
     * @throws IOException 获取失败时抛出
     */
    public InputStream downloadAsStream(String fileId) throws IOException {
        return getDriveService().files().get(fileId).executeMediaAsInputStream();
    }

    // ==================== 查询 ====================

    /**
     * 获取文件详细信息
     *
     * @param fileId Google Drive 文件 ID
     * @return 文件信息对象，包含 id、name、size、mimeType、webViewLink、webContentLink
     * @throws IOException 获取失败时抛出
     */
    public File getFileInfo(String fileId) throws IOException {
        return getDriveService().files().get(fileId)
                .setFields("id, name, size, mimeType, webViewLink, webContentLink, createdTime, modifiedTime")
                .execute();
    }

    /**
     * 列出指定文件夹下的所有文件（不含子文件夹中的文件）
     *
     * @param folderId 文件夹 ID
     * @return 文件列表，每个文件包含 id、name、size、mimeType
     * @throws IOException 查询失败时抛出
     */
    public List<File> listFiles(String folderId) throws IOException {
        String query = String.format("'%s' in parents and trashed = false", folderId);
        FileList result = getDriveService().files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, size, mimeType)")
                .setPageSize(1000)
                .execute();

        return result.getFiles();
    }

    /**
     * 按文件名关键词搜索文件（模糊匹配，在整个云盘范围内搜索）
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件列表，每个文件包含 id、name、size、mimeType
     * @throws IOException 搜索失败时抛出
     */
    public List<File> searchFiles(String keyword) throws IOException {
        String query = String.format("name contains '%s' and trashed = false", keyword);
        FileList result = getDriveService().files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, size, mimeType)")
                .setPageSize(1000)
                .execute();

        return result.getFiles();
    }

    /**
     * 检查文件是否已存在于指定文件夹中（按文件名精确匹配）
     *
     * @param fileName 文件名
     * @param folderId 文件夹 ID
     * @return true 表示已存在，false 表示不存在
     * @throws IOException 查询失败时抛出
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

    // ==================== 链接 ====================

    /**
     * 获取文件的网页预览链接（在 Google Drive 网页端打开）
     * 注意：访问者需要有权限才能打开，可配合 shareFilePublic 使用
     *
     * @param fileId Google Drive 文件 ID
     * @return 网页预览链接
     * @throws IOException 获取失败时抛出
     */
    public String getPreviewLink(String fileId) throws IOException {
        File file = getDriveService().files().get(fileId)
                .setFields("webViewLink")
                .execute();
        return file.getWebViewLink();
    }

    /**
     * 获取文件的直接下载链接
     * 注意：仅对非 Google 原生格式文件有效（如 pdf、mp4 等），且访问者需要有权限
     *
     * @param fileId Google Drive 文件 ID
     * @return 直接下载链接，Google 原生格式文件（如 Google Docs）返回 null
     * @throws IOException 获取失败时抛出
     */
    public String getDownloadLink(String fileId) throws IOException {
        File file = getDriveService().files().get(fileId)
                .setFields("webContentLink")
                .execute();
        return file.getWebContentLink();
    }

    // ==================== 共享 ====================

    /**
     * 将文件共享给指定邮箱用户
     *
     * @param fileId Google Drive 文件 ID
     * @param email  目标用户的邮箱地址
     * @param role   权限角色："reader"（只读）、"writer"（可编辑）、"commenter"（可评论）
     * @throws IOException 共享失败时抛出
     */
    public void shareFile(String fileId, String email, String role) throws IOException {
        Permission permission = new Permission();
        permission.setType("user");
        permission.setRole(role);
        permission.setEmailAddress(email);

        getDriveService().permissions().create(fileId, permission)
                .setSendNotificationEmail(false)
                .execute();

        log.info("文件已共享: fileId={}, email={}, role={}", fileId, email, role);
    }

    /**
     * 将文件设为公开（任何人通过链接即可访问）
     * 设置后可通过 getPreviewLink / getDownloadLink 获取公开链接
     *
     * @param fileId Google Drive 文件 ID
     * @throws IOException 操作失败时抛出
     */
    public void shareFilePublic(String fileId) throws IOException {
        Permission permission = new Permission();
        permission.setType("anyone");
        permission.setRole("reader");

        getDriveService().permissions().create(fileId, permission).execute();

        log.info("文件已设为公开: fileId={}", fileId);
    }

    // ==================== 修改 ====================

    /**
     * 移动文件到另一个文件夹
     *
     * @param fileId      要移动的文件 ID
     * @param newFolderId 目标文件夹 ID
     * @throws IOException 操作失败时抛出
     */
    public void moveFile(String fileId, String newFolderId) throws IOException {
        File file = getDriveService().files().get(fileId)
                .setFields("parents")
                .execute();

        String previousParents = String.join(",", file.getParents());

        getDriveService().files().update(fileId, null)
                .setAddParents(newFolderId)
                .setRemoveParents(previousParents)
                .setFields("id, parents")
                .execute();

        log.info("文件已移动: fileId={}, newFolderId={}", fileId, newFolderId);
    }

    /**
     * 重命名文件
     *
     * @param fileId  要重命名的文件 ID
     * @param newName 新文件名（含扩展名，如 "new_name.mp4"）
     * @throws IOException 操作失败时抛出
     */
    public void renameFile(String fileId, String newName) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(newName);

        getDriveService().files().update(fileId, fileMetadata)
                .setFields("id, name")
                .execute();

        log.info("文件已重命名: fileId={}, newName={}", fileId, newName);
    }

    /**
     * 删除文件（永久删除，不进入回收站）
     *
     * @param fileId 要删除的文件 ID
     * @throws IOException 删除失败时抛出
     */
    public void deleteFile(String fileId) throws IOException {
        getDriveService().files().delete(fileId).execute();
        log.info("文件已删除: fileId={}", fileId);
    }
}
