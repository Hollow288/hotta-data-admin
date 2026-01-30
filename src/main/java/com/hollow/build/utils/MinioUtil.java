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
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @description： minio工具类
 * @version：3.0
 */
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private final MinioClient minioClient;

    private final MinioConfigurationProperties minioConfigurationProperties;

    /**
     * description: 判断bucket是否存在，不存在则创建
     *
     * @return: void
     */
    @SneakyThrows(Exception.class)
    public void existBucket(String name) {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(name).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(name).build());
        }
    }

    /**
     * 创建存储bucket
     *
     * @param bucketName 存储bucket名称
     * @return Boolean
     */
    public Boolean makeBucket(String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 删除存储bucket
     *
     * @param bucketName 存储bucket名称
     * @return Boolean
     */
    public Boolean removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * description: 上传文件
     *
     * @param multipartFile
     * @return: java.lang.String
     */
    // 多文件上传
    @SneakyThrows(Exception.class)
    public List<String> upload(MultipartFile[] multipartFile, String bucketName, String filePath){
        List<String> names = new ArrayList<>(multipartFile.length);
        for (MultipartFile file : multipartFile) {
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                String[] split = fileName.split("\\.");
                if (split.length > 1) {
                    StringBuilder filenameTem = new StringBuilder();
                    for (int i = 0; i < split.length; i++) {
                        if (i != split.length - 1) {
                            filenameTem.append(split[i]);
                        } else {
                            filenameTem.append("_").append(System.currentTimeMillis())
                                    .append(".").append(split[split.length - 1]);
                        }
                    }
                    fileName = filenameTem.toString();
                } else {
                    fileName = fileName + "_" + System.currentTimeMillis();
                }
            } else {
                fileName = "file_" + System.currentTimeMillis();
            }

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

            names.add(fileName);
        }
        return names;
    }

    // 单文件上传
    @SneakyThrows(Exception.class)
    public String upload(MultipartFile file, String bucketName, String filePath){
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String[] split = fileName.split("\\.");
            if (split.length > 1) {
                StringBuilder filenameTem = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    if (i != split.length - 1) {
                        filenameTem.append(split[i]);
                    } else {
                        filenameTem.append("_").append(System.currentTimeMillis())
                                .append(".").append(split[split.length - 1]);
                    }
                }
                fileName = filenameTem.toString();
            } else {
                fileName = fileName + "_" + System.currentTimeMillis();
            }
        } else {
            fileName = "file_" + System.currentTimeMillis();
        }

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
     * 查看文件对象
     *
     * @param bucketName 存储bucket名称
     * @return 存储bucket内文件对象信息
     */
    public List<HashMap<String,Object>> listObjects(String bucketName) {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).build());
        List<HashMap<String,Object>> objectItems = new ArrayList<>();
        try {
            for (Result<Item> result : results) {
                Item item = result.get();
                HashMap<String,Object> objectItem = new HashMap<>();
                objectItem.put("name",item.objectName());
                objectItem.put("size",item.size());
                objectItems.add(objectItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return objectItems;
    }

    /**
     * 批量删除文件对象
     *
     * @param bucketName 存储bucket名称
     * @param objects    对象名称集合
     */
    public Iterable<Result<DeleteError>> removeObjects(String bucketName, List<String> objects) {
        List<DeleteObject> dos = objects.stream().map(e -> new DeleteObject(e)).collect(Collectors.toList());
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder().bucket(bucketName).objects(dos).build());
        return results;
    }

    /**
     * 判断桶是否存在
     */
    @SneakyThrows(Exception.class)
    public boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }


    /**
     * 创建桶
     *
     * @param bucketName 获取全部的桶  minioClient.listBuckets();
     */
    @SneakyThrows(Exception.class)
    public void createBucket(String bucketName) {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }


    /**
     * 根据bucketName获取信息
     *
     * @param bucketName bucket名称
     */
    @SneakyThrows(Exception.class)
    public Optional<Bucket> getBucket(String bucketName) {
        return minioClient.listBuckets().stream().filter(b -> b.name().equals(bucketName)).findFirst();
    }


    /**
     * 获取文件流
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @return 二进制流
     */
    @SneakyThrows(Exception.class)
    public InputStream getObject(String bucketName, String objectName) {
        return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }


    /**
     * 上传本地文件
     *
     * @param bucketName 存储桶
     * @param objectName 对象名称
     * @param fileName   本地文件路径
     */
    @SneakyThrows(Exception.class)
    public ObjectWriteResponse putObject(String bucketName, String objectName, String fileName) {
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }
        return minioClient.uploadObject(UploadObjectArgs.builder().bucket(bucketName).object(objectName).filename(fileName).build());
    }

    /**
     * 通过流上传文件
     *
     * @param bucketName  存储桶
     * @param objectName  文件对象
     * @param inputStream 文件流
     */
    @SneakyThrows(Exception.class)
    public ObjectWriteResponse putObject(String bucketName, String objectName, InputStream inputStream) {
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }
        return minioClient.putObject(PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(inputStream, inputStream.available(), -1).build());
    }

    /**
     * 获取文件外链
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param expires    过期时间
     * @return url
     */
    @SneakyThrows(Exception.class)
    public String getUploadObjectUrl(String bucketName, String objectName, Integer expires) {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT).bucket(bucketName)
                .object(objectName).expiry(expires).build());
    }

    /**
     * 下载文件
     * bucketName:桶名
     *
     * @param fileName: 文件名
     */
    @SneakyThrows(Exception.class)
    public void download(String bucketName, String fileName, HttpServletResponse response) {
        // 获取对象的元数据
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(fileName).build());
        response.setContentType(stat.contentType());
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
        InputStream is = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(fileName).build());
        IOUtils.copy(is, response.getOutputStream());
        is.close();
    }


    public String fileUrlEncoderChance(String originalPath) {

            if (originalPath == null || originalPath.isBlank()) {
                return "";
            }

            String fileName = originalPath.substring(originalPath.lastIndexOf('/') + 1);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20")
                    .replaceAll("%21", "!")
                    .replaceAll("%27", "'")
                    .replaceAll("%28", "(")
                    .replaceAll("%29", ")")
                    .replaceAll("%7E", "~");
            String baseUrl = originalPath.substring(0, originalPath.lastIndexOf('/') + 1);

            return minioConfigurationProperties.getEndpoint() + '/' + baseUrl + encodedFileName;
    }


    /**
     * 将原始文件路径生成可访问 URL，并可附加额外前缀路径
     *
     * @param originalPath 原始文件相对路径，例如 "Resources/Icon/skill/WeaponSkill/skill_bow_power.png"
     * @param prefix       可选前缀路径，例如 "images"，如果传 null 或空则不添加
     * @return 拼接后的完整 URL
     */
    public String fileUrlEncoderChance(String originalPath, String prefix) {
        if (originalPath == null || originalPath.isBlank()) {
            return "";
        }

        // 提取文件名
        String fileName = originalPath.substring(originalPath.lastIndexOf('/') + 1);

        // URL 编码文件名
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20")
                .replaceAll("%21", "!")
                .replaceAll("%27", "'")
                .replaceAll("%28", "(")
                .replaceAll("%29", ")")
                .replaceAll("%7E", "~");

        // 提取路径前缀（原目录）
        String baseUrl = originalPath.substring(0, originalPath.lastIndexOf('/') + 1);

        // 处理可选 prefix，保证不为空且不重复 '/'
        String finalPrefix = (prefix == null || prefix.isBlank()) ? "" : prefix + "/";

        return minioConfigurationProperties.getEndpoint() + "/" + finalPrefix + baseUrl + encodedFileName;
    }


    /**
     * 上传分片文件 (不自动生成文件名，完全遵照传入的 path)
     *
     * @param bucketName 桶名
     * @param objectName 完整路径 (例如: temp/MD5值/1)
     * @param inputStream 文件流
     * @param contentType 类型
     * @return 成功返回 true
     */
    @SneakyThrows(Exception.class)
    public boolean uploadChunk(String bucketName, String objectName, InputStream inputStream, String contentType) {
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, inputStream.available(), -1) // -1 代表不预知流大小，但在分片上传中最好前端传大小，这里简化处理
                        .contentType(contentType)
                        .build()
        );
        return true;
    }


    /**
     * 合并分片文件
     *
     * @param bucketName 桶名
     * @param chunkNames 分片文件名称列表 (有序，例如: [temp/md5/1, temp/md5/2...])
     * @param targetName 合并后的目标文件名 (例如: video/2023/movie.mp4)
     * @param contentType 文件类型，例如 "video/mp4"
     * @return boolean
     */
    @SneakyThrows(Exception.class)
    public boolean composeFile(String bucketName, String targetName, List<String> chunkNames, String contentType) {
        if (chunkNames == null || chunkNames.isEmpty()) {
            return false;
        }

        List<ComposeSource> sources = chunkNames.stream()
                .map(chunkName -> ComposeSource.builder()
                        .bucket(bucketName)
                        .object(chunkName)
                        .build())
                .collect(Collectors.toList());

        // --- 核心修改：设置 Content-Type ---
        Map<String, String> headers = new HashMap<>();
        // 如果没传类型，给个默认值，但最好是传 video/mp4
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        headers.put("Content-Type", contentType);
        // -------------------------------

        minioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName)
                        .object(targetName)
                        .sources(sources)
                        .headers(headers) // <--- 注入 Header
                        .build()
        );

        return true;
    }


    /**
     * 获取文件预览/下载链接 (GET请求)
     *
     * @param bucketName 桶名
     * @param objectName 文件路径
     * @param expires    过期时间(秒)
     * @return 预览URL
     */
    @SneakyThrows(Exception.class)
    public String getPreviewFileUrl(String bucketName, String objectName, Integer expires) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET) // <--- 关键点：必须是 GET
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires) // 直接写死一天，或者作为参数传入
                        .build()
        );
    }


    /**
     * 获取已上传的分片列表
     * @param bucketName 桶名
     * @param identifier 文件的MD5 (作为文件夹名)
     * @return 已存在的索引列表 [1, 2, 3...]
     */
    @SneakyThrows(Exception.class)
    public List<Integer> getChunkIndices(String bucketName, String identifier) {
        List<Integer> chunkIndices = new ArrayList<>();

        // 构造前缀，例如: temp/a8d8c.../
        String prefix = "temp/" + identifier + "/";

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix) // 只查这个文件夹下的
                        .recursive(true)
                        .build()
        );

        for (Result<Item> result : results) {
            Item item = result.get();
            String objectName = item.objectName();
            // objectName 可能是 "temp/abc/1"
            // 我们需要提取最后的数字 "1"
            try {
                String fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
                chunkIndices.add(Integer.parseInt(fileName));
            } catch (NumberFormatException e) {
                // 忽略非数字文件
            }
        }
        return chunkIndices;
    }

}
 