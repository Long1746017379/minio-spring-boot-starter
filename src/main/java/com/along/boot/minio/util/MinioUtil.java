package com.along.boot.minio.util;

import com.along.boot.minio.callback.ProgressListener;
import com.along.boot.minio.template.MinioTemplate;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.ObjectWriteResponse;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MinIO 业务层工具类，基于 {@link MinioTemplate} 提供更便捷的业务方法。
 * <p>封装了诸如 MultipartFile 上传、文件夹判断、前缀查询、断点下载等高频业务场景。</p>
 *
 * @author along
 */
public class MinioUtil {

    private static final Logger log = LoggerFactory.getLogger(MinioUtil.class);

    private final MinioTemplate minioTemplate;

    public MinioUtil(MinioTemplate minioTemplate) {
        this.minioTemplate = minioTemplate;
    }

    // ===================== 上传 =====================

    /**
     * 上传文件并返回外链。
     *
     * @param bucket      Bucket 名称
     * @param objectName  对象名
     * @param inputStream 文件流
     * @param contentType 内容类型
     * @return 文件外链
     */
    public String upload(String bucket, String objectName, InputStream inputStream, String contentType) {
        minioTemplate.upload(bucket, objectName, inputStream, contentType);
        return buildObjectUrl(bucket, objectName);
    }

    /**
     * 上传 MultipartFile 并返回外链。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @param file       Spring MultipartFile
     * @return 文件外链
     */
    public String upload(String bucket, String objectName, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioTemplate.upload(bucket, objectName, inputStream, file.getContentType());
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }

    /**
     * 上传文件并监听进度，返回外链。
     *
     * @param bucket           Bucket 名称
     * @param objectName       对象名
     * @param inputStream      文件流
     * @param contentType      内容类型
     * @param progressListener 进度监听器
     * @return 文件外链
     */
    public String upload(String bucket, String objectName, InputStream inputStream,
                         String contentType, ProgressListener progressListener) {
        minioTemplate.upload(bucket, objectName, inputStream, contentType, progressListener);
        return buildObjectUrl(bucket, objectName);
    }

    /**
     * 上传 MultipartFile 并监听进度，返回外链。
     *
     * @param bucket           Bucket 名称
     * @param objectName       对象名
     * @param file             Spring MultipartFile
     * @param progressListener 进度监听器
     * @return 文件外链
     */
    public String upload(String bucket, String objectName, MultipartFile file,
                         ProgressListener progressListener) {
        try (InputStream inputStream = file.getInputStream()) {
            minioTemplate.upload(bucket, objectName, inputStream, file.getContentType(), progressListener);
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }

    // ===================== 下载 =====================

    /**
     * 获取文件流。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @return 文件输入流
     */
    public InputStream download(String bucket, String objectName) {
        return minioTemplate.download(bucket, objectName);
    }

    /**
     * 断点下载，获取文件指定范围的数据流。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param offset     起始字节位置
     * @param length     读取长度
     * @return 二进制流
     * @throws Exception 操作失败时抛出
     */
    public InputStream getObject(String bucketName, String objectName, long offset, long length) throws Exception {
        return minioTemplate.execute(minioClient -> minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build()));
    }

    // ===================== 删除 =====================

    /**
     * 删除单个文件。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     */
    public void delete(String bucket, String objectName) {
        minioTemplate.remove(bucket, objectName);
    }

    /**
     * 批量删除文件。
     *
     * @param bucket      Bucket 名称
     * @param objectNames 对象名列表
     */
    public void delete(String bucket, List<String> objectNames) {
        minioTemplate.remove(bucket, objectNames);
    }

    // ===================== Bucket 操作 =====================

    /**
     * 判断 Bucket 是否存在。
     *
     * @param bucketName Bucket 名称
     * @return true: 存在; false: 不存在
     */
    public boolean bucketExists(String bucketName) {
        return minioTemplate.bucketExists(bucketName);
    }

    /**
     * 获取 Bucket 策略（JSON 格式）。
     *
     * @param bucketName Bucket 名称
     * @return 策略 JSON
     */
    public String getBucketPolicy(String bucketName) {
        return minioTemplate.getBucketPolicy(bucketName);
    }

    /**
     * 设置 Bucket 策略（JSON 格式）。
     *
     * @param bucketName Bucket 名称
     * @param policy     策略 JSON
     */
    public void setBucketPolicy(String bucketName, String policy) {
        minioTemplate.setBucketPolicy(bucketName, policy);
    }

    /**
     * 获取所有 Bucket。
     *
     * @return Bucket 列表
     */
    public List<Bucket> getAllBuckets() {
        return minioTemplate.getAllBuckets();
    }

    /**
     * 根据名称获取 Bucket。
     *
     * @param bucketName Bucket 名称
     * @return Optional 包裹的 Bucket
     */
    public Optional<Bucket> getBucket(String bucketName) {
        return minioTemplate.getBucket(bucketName);
    }

    /**
     * 删除 Bucket。
     *
     * @param bucketName Bucket 名称
     */
    public void removeBucket(String bucketName) {
        minioTemplate.removeBucket(bucketName);
    }

    // ===================== 文件操作 =====================

    /**
     * 判断文件是否存在（通过 ErrorResponse 精确区分 "NoSuchKey"）。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @return true: 存在; false: 不存在
     */
    public boolean isObjectExistBak(String bucket, String objectName) {
        log.info("Checking if object exists: {}", objectName);
        return minioTemplate.execute(client -> {
            try {
                client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
                return true;
            } catch (ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    return false;
                }
                throw e;
            }
        });
    }

    /**
     * 判断文件是否存在（简单模式，异常时返回 false）。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return true: 存在; false: 不存在或发生异常
     */
    public boolean isObjectExist(String bucketName, String objectName) {
        boolean exist = true;
        try {
            minioTemplate.execute(minioClient -> minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build()));
        } catch (Exception e) {
            log.error("Check object existence failed", e);
            exist = false;
        }
        return exist;
    }

    /**
     * 判断文件夹是否存在。
     *
     * @param bucketName Bucket 名称
     * @param objectName 文件夹名称（以 / 结尾）
     * @return true: 存在; false: 不存在
     */
    public boolean isFolderExist(String bucketName, String objectName) {
        try {
            Iterable<Result<Item>> results = minioTemplate.execute(minioClient -> minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).recursive(false).build()));
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() && objectName.equals(item.objectName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Check folder existence failed", e);
        }
        return false;
    }

    /**
     * 根据前缀查询文件列表。
     *
     * @param bucketName Bucket 名称
     * @param prefix     文件名前缀
     * @param recursive  是否递归查询子目录
     * @return Item 列表
     * @throws Exception 操作失败时抛出
     */
    public List<Item> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive) throws Exception {
        List<Item> list = new ArrayList<>();
        Iterable<Result<Item>> objectsIterator = minioTemplate.execute(minioClient -> minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(recursive).build()));
        if (objectsIterator != null) {
            for (Result<Item> o : objectsIterator) {
                Item item = o.get();
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取指定前缀下的对象迭代器。
     *
     * @param bucketName Bucket 名称
     * @param prefix     前缀
     * @param recursive  是否递归
     * @return 结果迭代器
     */
    public Iterable<Result<Item>> listObjects(String bucketName, String prefix, boolean recursive) {
        return minioTemplate.execute(minioClient -> minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(recursive)
                        .build()));
    }

    /**
     * 创建空目录。
     *
     * @param bucketName Bucket 名称
     * @param objectName 目录路径（以 / 结尾）
     * @return 写入响应
     */
    public ObjectWriteResponse createDir(String bucketName, String objectName) {
        return minioTemplate.createDir(bucketName, objectName);
    }

    /**
     * 获取文件信息。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 文件元数据字符串
     */
    public String getFileStatusInfo(String bucketName, String objectName) {
        return minioTemplate.execute(minioClient -> minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()).toString());
    }

    /**
     * 拷贝文件。
     *
     * @param bucketName    源 Bucket
     * @param objectName    源对象名
     * @param srcBucketName 目标 Bucket
     * @param srcObjectName 目标对象名
     * @return 写入响应
     */
    public ObjectWriteResponse copyFile(String bucketName, String objectName,
                                        String srcBucketName, String srcObjectName) {
        return minioTemplate.copyFile(bucketName, objectName, srcBucketName, srcObjectName);
    }

    // ===================== 预签名 URL =====================

    /**
     * 获取 1 小时有效期的文件外链。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 外链 URL
     */
    public String getUrlExpiredOneHour(String bucketName, String objectName) {
        return getObjectUrl(bucketName, objectName, 60 * 60);
    }

    /**
     * 获取文件外链（自定义过期时间）。
     *
     * @param bucketName    Bucket 名称
     * @param objectName    对象名
     * @param expirySeconds 过期时间（秒）
     * @return 外链 URL
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expirySeconds) {
        return minioTemplate.getObjectUrl(bucketName, objectName, expirySeconds);
    }

    /**
     * 获取文件外链（默认 7 天）。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 外链 URL
     */
    public String getObjectUrl(String bucketName, String objectName) {
        return minioTemplate.getObjectUrl(bucketName, objectName);
    }

    /**
     * 批量获取文件外链。
     *
     * @param bucketName    Bucket 名称
     * @param objectNames   对象名列表
     * @param expirySeconds 过期时间（秒）
     * @return 外链 URL 列表
     */
    public List<String> getObjectUrls(String bucketName, List<String> objectNames, Integer expirySeconds) {
        return minioTemplate.getObjectUrls(bucketName, objectNames, expirySeconds);
    }

    // ===================== 秒传（内容哈希） =====================

    /**
     * 秒传上传文件：自动计算 MD5 哈希判断是否为重复文件。
     * <p>若文件内容已存在，通过服务器端复制实现瞬时完成。</p>
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @param file       Spring MultipartFile
     * @return 文件外链
     */
    public String instantUpload(String bucket, String objectName, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            ObjectWriteResponse response = minioTemplate.uploadWithInstantUpload(
                    bucket, objectName, inputStream, file.getContentType(), null);
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Instant upload failed", e);
        }
    }

    /**
     * 秒传上传文件（带进度监听）。
     *
     * @param bucket           Bucket 名称
     * @param objectName       对象名
     * @param file             Spring MultipartFile
     * @param progressListener 进度监听器
     * @return 文件外链
     */
    public String instantUpload(String bucket, String objectName, MultipartFile file,
                                 ProgressListener progressListener) {
        try (InputStream inputStream = file.getInputStream()) {
            ObjectWriteResponse response = minioTemplate.uploadWithInstantUpload(
                    bucket, objectName, inputStream, file.getContentType(), progressListener);
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Instant upload failed", e);
        }
    }

    /**
     * 判断文件内容是否已存在（秒传判断）。
     *
     * @param bucket Bucket 名称
     * @param hash   MD5 内容哈希值
     * @return true 表示相同内容的文件已存在
     */
    public boolean isContentDuplicate(String bucket, String hash) {
        return minioTemplate.isContentHashExist(bucket, hash);
    }

    /**
     * 计算文件内容的 MD5 哈希值。
     *
     * @param stream 输入流
     * @return MD5 十六进制小写字符串
     */
    public String computeMD5(InputStream stream) {
        return MinioHashUtil.computeMD5(stream);
    }

    // ===================== 分片上传（断点续传） =====================

    /**
     * 断点续传上传文件。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @param file       Spring MultipartFile
     * @param partSize   分片大小（字节），建议 5MB ~ 50MB
     * @param uploadId   续传的 uploadId（首次上传传 null）
     * @return 文件外链
     */
    public String resumeUpload(String bucket, String objectName,
                                MultipartFile file, int partSize, String uploadId) {
        try (InputStream inputStream = file.getInputStream()) {
            ObjectWriteResponse response = minioTemplate.resumeUpload(
                    bucket, objectName, inputStream, file.getSize(), partSize, uploadId);
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Resume upload failed", e);
        }
    }

    /**
     * 初始化分片上传。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @return uploadId
     */
    public String initiateMultipartUpload(String bucket, String objectName) {
        return minioTemplate.initiateMultipartUpload(bucket, objectName);
    }

    /**
     * 列举已上传的分片。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @return 分片列表
     */
    public List<Part> listParts(String bucket, String objectName, String uploadId) {
        return minioTemplate.listParts(bucket, objectName, uploadId);
    }

    /**
     * 取消分片上传。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     */
    public void abortMultipartUpload(String bucket, String objectName, String uploadId) {
        minioTemplate.abortMultipartUpload(bucket, objectName, uploadId);
    }

    // ===================== 工具 =====================

    /**
     * 将 URLDecoder 编码转为 UTF-8。
     * <p>先对未编码的中文字符做百分号转义，再进行 UTF-8 解码。</p>
     *
     * @param str 原始字符串
     * @return UTF-8 解码后的字符串
     * @throws UnsupportedEncodingException 编码不支持时抛出
     */
    public String getUTF8ByURLDecoder(String str) throws UnsupportedEncodingException {
        String url = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        return URLDecoder.decode(url, "UTF-8");
    }

    /**
     * 构建文件外链。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象名
     * @return 外链 URL
     */
    private String buildObjectUrl(String bucket, String objectName) {
        return getObjectUrl(bucket, objectName);
    }

}
