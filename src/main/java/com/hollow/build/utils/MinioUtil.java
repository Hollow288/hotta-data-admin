package com.hollow.build.utils;

import com.hollow.build.config.MinioConfigurationProperties;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MinIO 对象存储工具类
 * 提供 bucket 管理、文件上传/下载、URL 生成、分片上传合并等功能
 *
 * @version 4.0
 */
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private final MinioClient minioClient;
    private final MinioConfigurationProperties minioConfigurationProperties;

    /** 预签名 URL 默认过期时间：24 小时（单位：秒） */
    private static final int DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS = 24 * 60 * 60;

    // ======================== 内部类 ========================

    /**
     * 表示 MinIO 对象的存储位置（bucket 名 + object 路径）
     */
    private static final class ObjectLocation {
        private final String bucketName;
        private final String objectName;

        private ObjectLocation(String bucketName, String objectName) {
            this.bucketName = bucketName;
            this.objectName = objectName;
        }

        public String getBucketName() { return bucketName; }
        public String getObjectName()  { return objectName; }
    }

    // ======================== Bucket 管理 ========================

    /**
     * 判断 bucket 是否存在，不存在则创建
     *
     * @param bucketName bucket 名称
     */
    @SneakyThrows(Exception.class)
    public void ensureBucketExists(String bucketName) {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 判断 bucket 是否存在
     *
     * @param bucketName bucket 名称
     * @return true 表示存在
     */
    @SneakyThrows(Exception.class)
    public boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 删除 bucket
     *
     * @param bucketName bucket 名称
     * @return true 表示删除成功
     */
    public boolean removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据名称查找 bucket
     *
     * @param bucketName bucket 名称
     * @return Optional<Bucket>
     */
    @SneakyThrows(Exception.class)
    public Optional<Bucket> getBucket(String bucketName) {
        return minioClient.listBuckets().stream()
                .filter(b -> b.name().equals(bucketName))
                .findFirst();
    }

    // ======================== 文件上传 ========================

    /**
     * 批量上传文件（自动在扩展名前追加时间戳以防重名）
     *
     * @param multipartFile 文件数组
     * @param bucketName    目标 bucket
     * @param filePath      存储目录路径（以 "/" 结尾）
     * @return 实际存储的文件名列表
     */
    @SneakyThrows(Exception.class)
    public List<String> upload(MultipartFile[] multipartFile, String bucketName, String filePath) {
        List<String> names = new ArrayList<>(multipartFile.length);
        for (MultipartFile file : multipartFile) {
            names.add(uploadSingle(file, bucketName, filePath));
        }
        return names;
    }

    /**
     * 上传单个 MultipartFile（自动在扩展名前追加时间戳以防重名）
     *
     * @param file       上传的文件
     * @param bucketName 目标 bucket
     * @param filePath   存储目录路径（以 "/" 结尾）
     * @return 实际存储的文件名
     */
    @SneakyThrows(Exception.class)
    public String upload(MultipartFile file, String bucketName, String filePath) {
        return uploadSingle(file, bucketName, filePath);
    }

    /**
     * 上传本地文件到 MinIO
     *
     * @param bucketName 目标 bucket
     * @param objectName 存储对象名称（含路径）
     * @param fileName   本地文件路径
     * @return 写入响应
     */
    @SneakyThrows(Exception.class)
    public ObjectWriteResponse putObject(String bucketName, String objectName, String fileName) {
        ensureBucketExists(bucketName);
        return minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(fileName)
                        .build()
        );
    }

    /**
     * 通过输入流上传文件到 MinIO
     *
     * @param bucketName  目标 bucket
     * @param objectName  存储对象名称（含路径）
     * @param inputStream 文件输入流
     * @return 写入响应
     */
    @SneakyThrows(Exception.class)
    public ObjectWriteResponse putObject(String bucketName, String objectName, InputStream inputStream) {
        ensureBucketExists(bucketName);
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, inputStream.available(), -1)
                        .build()
        );
    }

    /**
     * 上传分片文件（不自动生成文件名，完全遵照传入的 objectName）
     *
     * @param bucketName  目标 bucket
     * @param objectName  完整存储路径，例如 "temp/MD5值/1"
     * @param inputStream 分片文件流
     * @param contentType 内容类型
     * @return true 表示上传成功
     */
    @SneakyThrows(Exception.class)
    public boolean uploadChunk(String bucketName, String objectName,
                               InputStream inputStream, String contentType) {
        ensureBucketExists(bucketName);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, inputStream.available(), -1)
                        .contentType(contentType)
                        .build()
        );
        return true;
    }

    /**
     * 合并分片文件为完整文件
     *
     * @param bucketName  目标 bucket
     * @param targetName  合并后的目标对象名，例如 "video/2023/movie.mp4"
     * @param chunkNames  有序分片对象名列表，例如 ["temp/md5/1", "temp/md5/2", ...]
     * @param contentType 目标文件 MIME 类型，例如 "video/mp4"
     * @return true 表示合并成功；chunkNames 为空时返回 false
     */
    @SneakyThrows(Exception.class)
    public boolean composeFile(String bucketName, String targetName,
                               List<String> chunkNames, String contentType) {
        if (chunkNames == null || chunkNames.isEmpty()) {
            return false;
        }

        List<ComposeSource> sources = chunkNames.stream()
                .map(name -> ComposeSource.builder().bucket(bucketName).object(name).build())
                .collect(Collectors.toList());

        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        minioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName)
                        .object(targetName)
                        .sources(sources)
                        .headers(Collections.singletonMap("Content-Type", contentType))
                        .build()
        );
        return true;
    }

    // ======================== 文件查询与下载 ========================

    /**
     * 获取 bucket 内所有文件对象的基本信息（名称与大小）
     *
     * @param bucketName bucket 名称
     * @return 包含 "name" 和 "size" 字段的 Map 列表；发生异常时返回 null
     */
    public List<HashMap<String, Object>> listObjects(String bucketName) {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).build());
        List<HashMap<String, Object>> objectItems = new ArrayList<>();
        try {
            for (Result<Item> result : results) {
                Item item = result.get();
                HashMap<String, Object> objectItem = new HashMap<>();
                objectItem.put("name", item.objectName());
                objectItem.put("size", item.size());
                objectItems.add(objectItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return objectItems;
    }

    /**
     * 获取已上传的分片索引列表
     *
     * @param bucketName bucket 名称
     * @param identifier 文件唯一标识（通常为 MD5），作为分片目录名
     * @return 已存在的分片索引列表，例如 [1, 2, 3]
     */
    @SneakyThrows(Exception.class)
    public List<Integer> getChunkIndices(String bucketName, String identifier) {
        List<Integer> chunkIndices = new ArrayList<>();
        String prefix = "temp/" + identifier + "/";

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(true)
                        .build()
        );

        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            try {
                String fileName = objectName.substring(objectName.lastIndexOf('/') + 1);
                chunkIndices.add(Integer.parseInt(fileName));
            } catch (NumberFormatException e) {
                // 忽略非数字命名的文件
            }
        }
        return chunkIndices;
    }

    /**
     * 获取文件对象的输入流
     *
     * @param bucketName bucket 名称
     * @param objectName 对象名称（含路径）
     * @return 文件二进制输入流
     */
    @SneakyThrows(Exception.class)
    public InputStream getObject(String bucketName, String objectName) {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build()
        );
    }

    /**
     * 下载文件并写入 HTTP 响应（触发浏览器下载）
     *
     * @param bucketName bucket 名称
     * @param fileName   对象名称（含路径）
     * @param response   HttpServletResponse
     */
    @SneakyThrows(Exception.class)
    public void download(String bucketName, String fileName, HttpServletResponse response) {
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(fileName).build()
        );
        response.setContentType(stat.contentType());
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));

        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(fileName).build())) {
            IOUtils.copy(is, response.getOutputStream());
        }
    }

    // ======================== URL 生成 ========================

    /**
     * 生成文件上传预签名 URL（PUT 请求）
     *
     * @param bucketName bucket 名称
     * @param objectName 对象名称
     * @param expires    过期时间（秒）
     * @return 预签名上传 URL
     */
    @SneakyThrows(Exception.class)
    public String getUploadObjectUrl(String bucketName, String objectName, Integer expires) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires)
                        .build()
        );
    }

    /**
     * 生成文件预览/下载预签名 URL（GET 请求）
     *
     * @param bucketName bucket 名称
     * @param objectName 对象名称
     * @param expires    过期时间（秒）
     * @return 预签名访问 URL
     */
    @SneakyThrows(Exception.class)
    public String getPreviewFileUrl(String bucketName, String objectName, Integer expires) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires)
                        .build()
        );
    }

    /**
     * 根据原始路径构建可访问 URL（不含 bucket 前缀）
     *
     * @param originalPath 原始文件相对路径
     * @return 拼接后的完整 URL；路径为空时返回空字符串
     */
    public String fileUrlEncoderChance(String originalPath) {
        return buildLegacyPublicUrl(originalPath, null);
    }

    /**
     * 根据原始路径和可选前缀构建可访问 URL
     *
     * @param originalPath 原始文件相对路径，例如 "Resources/Icon/skill.png"
     * @param prefix       可选 bucket 前缀，例如 "images"；null 或空时不添加
     * @return 拼接后的完整 URL；路径为空时返回空字符串
     */
    public String fileUrlEncoderChance(String originalPath, String prefix) {
        return buildLegacyPublicUrl(originalPath, prefix);
    }

    /**
     * 构建带过期时间的预签名文件访问 URL。
     *
     * 优先尝试解析路径生成 MinIO 预签名 URL；
     * 若解析失败则降级为旧版公开 URL 拼接方式。
     *
     * @param originalPath 原始文件路径（可能含反斜杠或多余斜杠）
     * @param prefix       bucket 名称前缀（可为 null）
     * @return 可访问的文件 URL；输入为空时返回空字符串
     */
    @SneakyThrows(Exception.class)
    public String buildExpiringFileUrl(String originalPath, String prefix) {
        if (originalPath == null || originalPath.isBlank()) {
            return "";
        }

        String normalizedPath = normalizeObjectPath(originalPath);
        if (!normalizedPath.isBlank()) {
            ObjectLocation location = resolveObjectLocation(normalizedPath, prefix);
            if (location != null) {
                return minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(location.getBucketName())
                                .object(location.getObjectName())
                                .expiry(DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS)
                                .build()
                );
            }
        }

        return buildLegacyPublicUrl(originalPath, prefix);
    }

    // ======================== 批量删除 ========================

    /**
     * 批量删除 bucket 中的文件对象
     *
     * @param bucketName bucket 名称
     * @param objects    待删除的对象名称列表
     * @return 删除操作结果迭代器（含失败详情）
     */
    public Iterable<Result<DeleteError>> removeObjects(String bucketName, List<String> objects) {
        List<DeleteObject> deleteObjects = objects.stream()
                .map(DeleteObject::new)
                .collect(Collectors.toList());
        return minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucketName).objects(deleteObjects).build()
        );
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 单文件上传核心逻辑：生成带时间戳的文件名并写入 MinIO
     *
     * @param file       上传的文件
     * @param bucketName 目标 bucket
     * @param filePath   存储目录路径（以 "/" 结尾）
     * @return 实际存储的文件名
     */
    @SneakyThrows(Exception.class)
    private String uploadSingle(MultipartFile file, String bucketName, String filePath) {
        String fileName = generateFileName(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filePath + fileName)
                            .stream(in, in.available(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        return fileName;
    }

    /**
     * 根据原始文件名生成带时间戳的唯一文件名。
     *
     * 规则：在扩展名前插入 "_时间戳"，例如：
     * "photo.jpg" → "photo_1718000000000.jpg"
     * "readme"    → "readme_1718000000000"
     * null        → "file_1718000000000"
     *
     * @param originalName 原始文件名（可为 null）
     * @return 带时间戳的唯一文件名
     */
    private String generateFileName(String originalName) {
        long ts = System.currentTimeMillis();
        if (originalName == null) {
            return "file_" + ts;
        }
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalName.substring(0, dotIndex) + "_" + ts
                    + "." + originalName.substring(dotIndex + 1);
        }
        return originalName + "_" + ts;
    }

    /**
     * 拼接旧版公开访问 URL。
     *
     * 拼接格式：{endpoint}/{prefix}/{目录路径}{URL编码后的文件名}
     *
     * @param originalPath 原始文件路径
     * @param prefix       bucket 前缀（可为 null）
     * @return 拼接好的公开 URL；路径为空时返回空字符串
     */
    private String buildLegacyPublicUrl(String originalPath, String prefix) {
        if (originalPath == null || originalPath.isBlank()) {
            return "";
        }

        String sanitizedPath = originalPath.replace("\\", "/");
        int lastSlash = sanitizedPath.lastIndexOf('/');

        String fileName = lastSlash >= 0 ? sanitizedPath.substring(lastSlash + 1) : sanitizedPath;
        String baseUrl  = lastSlash >= 0 ? sanitizedPath.substring(0, lastSlash + 1) : "";

        String encodedFileName = encodeFileName(fileName);
        String normalizedPrefix = normalizeBucketName(prefix);
        String finalPrefix = normalizedPrefix.isEmpty() ? "" : normalizedPrefix + "/";

        return minioConfigurationProperties.getEndpoint() + "/" + finalPrefix + baseUrl + encodedFileName;
    }

    /**
     * 对文件名进行 URL 编码，并还原部分无需转义的安全字符（! ' ( ) ~）。
     *
     * @param fileName 文件名
     * @return URL 编码后的文件名
     */
    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20")
                .replaceAll("%21", "!")
                .replaceAll("%27", "'")
                .replaceAll("%28", "(")
                .replaceAll("%29", ")")
                .replaceAll("%7E", "~");
    }

    /**
     * 标准化 bucket 名称：去除首尾空白及首尾斜杠。
     *
     * @param prefix 原始 bucket 名称或前缀
     * @return 标准化后的名称；null 或空白时返回空字符串
     */
    private String normalizeBucketName(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return stripTrailingSlashes(stripLeadingSlashes(prefix.trim()));
    }

    /**
     * 标准化对象路径：将反斜杠替换为正斜杠，并去除前导斜杠。
     *
     * @param originalPath 原始路径
     * @return 标准化后的路径
     */
    private String normalizeObjectPath(String originalPath) {
        return stripLeadingSlashes(originalPath.trim().replace("\\", "/"));
    }

    /**
     * 去除字符串开头的所有斜杠。
     *
     * @param value 输入字符串
     * @return 处理后的字符串；null 时返回空字符串
     */
    private String stripLeadingSlashes(String value) {
        if (value == null) return "";
        int i = 0;
        while (i < value.length() && value.charAt(i) == '/') i++;
        return value.substring(i);
    }

    /**
     * 去除字符串末尾的所有斜杠。
     *
     * @param value 输入字符串
     * @return 处理后的字符串；null 时返回空字符串
     */
    private String stripTrailingSlashes(String value) {
        if (value == null) return "";
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    /**
     * 从标准化路径中解析出 MinIO 对象位置（bucket 名 + object 路径）。
     *
     * 解析规则：
     * - prefix 非空：路径以 "{prefix}/" 开头时去掉前缀，否则直接作为 objectPath
     * - prefix 为空：路径第一段（首个 '/' 前）作为 bucketName，后续为 objectPath
     *
     * @param normalizedPath 已标准化的路径（无前导斜杠）
     * @param prefix         bucket 名称前缀（可为 null）
     * @return 解析成功返回 ObjectLocation；格式不合法时返回 null
     */
    private ObjectLocation resolveObjectLocation(String normalizedPath, String prefix) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }

        String bucketName = normalizeBucketName(prefix);
        String objectPath = normalizedPath;

        if (!bucketName.isEmpty()) {
            String prefixWithSlash = bucketName + "/";
            if (objectPath.startsWith(prefixWithSlash)) {
                objectPath = objectPath.substring(prefixWithSlash.length());
            }
        } else {
            int slashIndex = objectPath.indexOf('/');
            if (slashIndex <= 0) return null;
            bucketName = objectPath.substring(0, slashIndex);
            objectPath = objectPath.substring(slashIndex + 1);
        }

        objectPath = stripLeadingSlashes(objectPath);
        if (bucketName.isEmpty() || objectPath.isEmpty()) return null;

        return new ObjectLocation(bucketName, objectPath);
    }
}