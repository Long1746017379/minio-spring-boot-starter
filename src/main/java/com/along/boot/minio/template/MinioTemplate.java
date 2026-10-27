package com.along.boot.minio.template;

import com.along.boot.minio.callback.MinioCallback;
import com.along.boot.minio.callback.ProgressListener;
import com.along.boot.minio.model.ContentCheckResult;
import com.along.boot.minio.model.MultipartUploadInfo;
import com.along.boot.minio.model.MultipartUploadStatus;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectResponse;
import io.minio.UploadPartResponse;
import io.minio.messages.Bucket;
import io.minio.messages.Part;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * MinIO 操作模板接口，参考 RedisTemplate 设计理念。
 * <p>封装 MinIO 对象存储的常用操作，并提供 {@link #execute(MinioCallback)} 方法暴露原生客户端以便扩展。</p>
 *
 * @author along
 */
public interface MinioTemplate {

    // ===================== Bucket 操作 =====================

    /**
     * 判断 Bucket 是否存在。
     *
     * @param bucketName Bucket 名称
     * @return true: 存在; false: 不存在
     */
    boolean bucketExists(String bucketName);

    /**
     * 如果 Bucket 不存在则创建。
     *
     * @param bucketName Bucket 名称
     */
    void createBucketIfAbsent(String bucketName);

    /**
     * 获取所有 Bucket 列表。
     *
     * @return Bucket 列表
     */
    List<Bucket> getAllBuckets();

    /**
     * 根据名称获取 Bucket。
     *
     * @param bucketName Bucket 名称
     * @return 包含 Bucket 的 Optional
     */
    Optional<Bucket> getBucket(String bucketName);

    /**
     * 删除 Bucket。
     *
     * @param bucketName Bucket 名称
     */
    void removeBucket(String bucketName);

    // ===================== 上传 =====================

    /**
     * 上传文件（使用默认 Bucket）。
     *
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 内容类型（如 application/octet-stream）
     */
    void upload(String objectName, InputStream stream, String contentType);

    /**
     * 上传文件。
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 内容类型
     */
    void upload(String bucketName, String objectName, InputStream stream, String contentType);

    /**
     * 上传文件并监听进度。
     *
     * @param bucketName       Bucket 名称
     * @param objectName       对象名
     * @param stream           文件流
     * @param contentType      内容类型
     * @param progressListener 进度监听器
     */
    void upload(String bucketName, String objectName, InputStream stream, String contentType,
                ProgressListener progressListener);

    /**
     * 上传文件（指定文件大小和分片大小，大文件建议使用此方法）。
     * <p>SDK 在 {@code objectSize > partSize} 时自动走分片上传。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 内容类型
     * @param objectSize  文件总大小（字节），-1 表示未知
     * @param partSize    分片大小（字节），建议 5MB ~ 50MB
     */
    void upload(String bucketName, String objectName, InputStream stream, String contentType,
                long objectSize, int partSize);

    // ===================== 下载 =====================

    /**
     * 下载文件（使用默认 Bucket）。
     *
     * @param objectName 对象名
     * @return 文件输入流
     */
    InputStream download(String objectName);

    /**
     * 下载文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 文件输入流
     */
    InputStream download(String bucketName, String objectName);

    // ===================== 删除 =====================

    /**
     * 删除文件（使用默认 Bucket）。
     *
     * @param objectName 对象名
     */
    void remove(String objectName);

    /**
     * 删除单个文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     */
    void remove(String bucketName, String objectName);

    /**
     * 批量删除文件。
     *
     * @param bucketName  Bucket 名称
     * @param objectNames 对象名列表
     */
    void remove(String bucketName, List<String> objectNames);

    // ===================== 查询 =====================

    /**
     * 列出 Bucket 内的所有对象名。
     *
     * @param bucketName Bucket 名称
     * @return 对象名列表
     */
    List<String> listObjects(String bucketName);

    /**
     * 获取对象信息。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 对象元数据
     */
    StatObjectResponse getObjectInfo(String bucketName, String objectName);

    /**
     * 判断文件是否存在。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return true: 存在; false: 不存在
     */
    boolean isObjectExist(String bucketName, String objectName);

    // ===================== 预签名 URL =====================

    /**
     * 获取文件外链（默认 7 天有效）。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return 预签名 URL
     */
    String getObjectUrl(String bucketName, String objectName);

    /**
     * 获取文件外链（自定义过期时间）。
     *
     * @param bucketName   Bucket 名称
     * @param objectName   对象名
     * @param expirySeconds 过期时间（秒），最大 604800（7 天）
     * @return 预签名 URL
     */
    String getObjectUrl(String bucketName, String objectName, Integer expirySeconds);

    /**
     * 批量获取文件外链。
     *
     * @param bucketName    Bucket 名称
     * @param objectNames   对象名列表
     * @param expirySeconds 过期时间（秒）
     * @return 预签名 URL 列表
     */
    List<String> getObjectUrls(String bucketName, List<String> objectNames, Integer expirySeconds);

    // ===================== 桶策略 =====================

    /**
     * 获取 Bucket 策略（JSON 格式）。
     *
     * @param bucketName Bucket 名称
     * @return 策略 JSON 字符串
     */
    String getBucketPolicy(String bucketName);

    /**
     * 设置 Bucket 策略（JSON 格式）。
     *
     * @param bucketName Bucket 名称
     * @param policy     策略 JSON 字符串
     */
    void setBucketPolicy(String bucketName, String policy);

    // ===================== 分片上传（断点续传） =====================

    /**
     * 初始化分片上传。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return uploadId 分片上传唯一标识
     */
    String initiateMultipartUpload(String bucketName, String objectName);

    /**
     * 初始化分片上传并指定 Content-Type。
     * <p>默认实现忽略 contentType，以保持与已有实现类的二进制兼容；
     * {@code DefaultMinioTemplate} 会真正写入对象元数据。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param contentType 内容类型（如 image/jpeg），为空时不设置
     * @return uploadId 分片上传唯一标识
     */
    default String initiateMultipartUpload(String bucketName, String objectName, String contentType) {
        return initiateMultipartUpload(bucketName, objectName);
    }

    /**
     * 初始化分片上传、指定 Content-Type，并登记内容哈希映射。
     * <p>登记后可以用 {@link #findUploadIdByHash(String, String)} 找回 uploadId。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param contentType 内容类型（如 image/jpeg），为空时不设置
     * @param hash        MD5 内容哈希值（32 位十六进制小写）
     * @return uploadId 分片上传唯一标识
     */
    default String initiateMultipartUpload(String bucketName, String objectName,
                                           String contentType, String hash) {
        String uploadId = initiateMultipartUpload(bucketName, objectName, contentType);
        registerUploadHash(bucketName, hash, objectName, uploadId);
        return uploadId;
    }

    /**
     * 上传单个分片。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @param partNumber 分片编号（从 1 开始）
     * @param stream     分片数据流
     * @param partSize   分片大小（字节）
     * @return UploadPartResponse
     */
    UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId,
                                  int partNumber, InputStream stream, long partSize);

    /**
     * 上传一个客户端切片。
     * <p>{@code chunkIndex} 从 0 开始，内部映射为 S3 的
     * {@code partNumber = chunkIndex + 1}。除最后一片外，{@code chunkSize} 必须不小于
     * 5MiB，且各片大小一致。</p>
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @param chunkIndex 切片编号（从 0 开始）
     * @param stream     切片数据流
     * @param chunkSize  切片大小（字节，必须与实际数据长度一致）
     * @return UploadPartResponse
     */
    default UploadPartResponse uploadChunk(String bucketName, String objectName, String uploadId,
                                           int chunkIndex, InputStream stream, long chunkSize) {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be >= 0");
        }
        return uploadPart(bucketName, objectName, uploadId, chunkIndex + 1, stream, chunkSize);
    }

    /**
     * 列举已上传的分片列表。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @return 已上传的分片列表
     */
    List<Part> listParts(String bucketName, String objectName, String uploadId);

    /**
     * 完成分片上传，合并所有分片。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @param parts      所有分片信息（partNumber + etag）
     * @return 写入响应
     */
    ObjectWriteResponse completeMultipartUpload(String bucketName, String objectName,
                                                String uploadId, Part[] parts);

    /**
     * 取消分片上传。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     */
    void abortMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 断点续传高阶接口：自动跳过已上传的分片，仅上传缺失分片。
     * <p>如果 uploadId 为 null，则新建分片上传；否则沿用已有 uploadId 继续上传。</p>
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param stream     文件输入流
     * @param objectSize 文件总大小（必须已知）
     * @param partSize   分片大小（字节），建议 5MB ~ 50MB
     * @param uploadId   null 则新建，非 null 则继续该 uploadId
     * @return 写入响应
     */
    ObjectWriteResponse resumeUpload(String bucketName, String objectName,
                                     InputStream stream, long objectSize, int partSize,
                                     String uploadId);

    /**
     * 断点续传高阶接口（指定 Content-Type）。
     * <p>默认实现忽略 contentType，以保持与已有实现类的二进制兼容；
     * {@code DefaultMinioTemplate} 会真正写入对象元数据。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param stream      文件输入流
     * @param objectSize  文件总大小（必须已知）
     * @param partSize    分片大小（字节），建议 5MB ~ 50MB
     * @param uploadId    null 则新建，非 null 则继续该 uploadId
     * @param contentType 内容类型，为空时不设置；仅在新建分片上传时生效
     * @return 写入响应
     */
    default ObjectWriteResponse resumeUpload(String bucketName, String objectName,
                                             InputStream stream, long objectSize, int partSize,
                                             String uploadId, String contentType) {
        return resumeUpload(bucketName, objectName, stream, objectSize, partSize, uploadId);
    }

    /**
     * 列举进行中的分片上传。
     *
     * @param bucketName Bucket 名称
     * @param prefix     对象名前缀
     * @return 进行中的上传列表
     */
    default List<MultipartUploadInfo> listMultipartUploads(String bucketName, String prefix) {
        throw new UnsupportedOperationException(
                "listMultipartUploads is not supported by this MinioTemplate implementation");
    }

    /**
     * 查找指定对象最近一次进行中的分片上传 uploadId。
     * <p>前提是对象名稳定（例如包含文件哈希），否则无法找回。</p>
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @return uploadId，若不存在则返回 empty
     */
    default Optional<String> findUploadId(String bucketName, String objectName) {
        MultipartUploadInfo latest = null;
        for (MultipartUploadInfo upload : listMultipartUploads(bucketName, objectName)) {
            if (!objectName.equals(upload.getObjectName())) {
                continue;
            }
            if (latest == null
                    || (upload.getInitiated() != null && latest.getInitiated() != null
                    && upload.getInitiated().isAfter(latest.getInitiated()))) {
                latest = upload;
            }
        }
        return latest == null ? Optional.empty() : Optional.of(latest.getUploadId());
    }

    /**
     * 查询分片上传进度。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     * @return 上传进度
     */
    default MultipartUploadStatus getUploadStatus(String bucketName, String objectName,
                                                  String uploadId) {
        List<Part> parts = listParts(bucketName, objectName, uploadId);
        List<Integer> partNumbers = new ArrayList<>();
        long uploadedBytes = 0;
        for (Part part : parts) {
            partNumbers.add(part.partNumber());
            uploadedBytes += part.partSize();
        }
        return new MultipartUploadStatus(bucketName, objectName, uploadId, partNumbers, uploadedBytes);
    }

    /**
     * 登记"内容哈希 → 进行中分片上传"的映射。
     * <p>默认实现不支持；{@code DefaultMinioTemplate} 会写入
     * {@code .__minio_uploads/{hash}} 标记对象。</p>
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @param objectName 对象名
     * @param uploadId   分片上传 ID
     */
    default void registerUploadHash(String bucketName, String hash, String objectName,
                                    String uploadId) {
        throw new UnsupportedOperationException(
                "registerUploadHash is not supported by this MinioTemplate implementation");
    }

    /**
     * 删除"内容哈希 → 进行中分片上传"的映射。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     */
    default void removeUploadHash(String bucketName, String hash) {
        throw new UnsupportedOperationException(
                "removeUploadHash is not supported by this MinioTemplate implementation");
    }

    /**
     * 根据内容哈希查找进行中的分片上传。
     * <p>实现会校验 uploadId 是否仍然有效；已完成或已取消的上传会被清理并返回 empty。</p>
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @return 上传信息；不存在或已失效时返回 empty
     */
    default Optional<MultipartUploadInfo> findUploadByHash(String bucketName, String hash) {
        throw new UnsupportedOperationException(
                "findUploadByHash is not supported by this MinioTemplate implementation");
    }

    /**
     * 根据内容哈希查找进行中分片上传的 uploadId。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @return uploadId，若不存在则返回 empty
     */
    default Optional<String> findUploadIdByHash(String bucketName, String hash) {
        return findUploadByHash(bucketName, hash).map(MultipartUploadInfo::getUploadId);
    }

    /**
     * 根据内容哈希查询分片上传进度。
     * <p>返回的 {@link MultipartUploadStatus} 可用
     * {@link MultipartUploadStatus#missingChunkIndexes(int)} 计算缺失切片。</p>
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @return 上传进度；不存在或已失效时返回 empty
     */
    default Optional<MultipartUploadStatus> getUploadStatusByHash(String bucketName, String hash) {
        return findUploadByHash(bucketName, hash)
                .map(upload -> getUploadStatus(bucketName, upload.getObjectName(),
                        upload.getUploadId()));
    }

    /**
     * 按内容哈希完成分片上传：合并分片、登记已完成内容的哈希、清理进行中映射。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @return 写入响应
     */
    default ObjectWriteResponse completeUploadByHash(String bucketName, String hash) {
        MultipartUploadInfo upload = findUploadByHash(bucketName, hash)
                .orElseThrow(() -> new IllegalStateException(
                        "No in-progress upload for hash " + hash));
        List<Part> parts = new ArrayList<>(listParts(bucketName, upload.getObjectName(),
                upload.getUploadId()));
        if (parts.isEmpty()) {
            throw new IllegalStateException("No uploaded parts for hash " + hash);
        }
        parts.sort(Comparator.comparingInt(Part::partNumber));
        ObjectWriteResponse response = completeMultipartUpload(bucketName, upload.getObjectName(),
                upload.getUploadId(), parts.toArray(new Part[0]));
        createContentHashMarker(bucketName, hash, upload.getObjectName());
        removeUploadHash(bucketName, hash);
        return response;
    }

    /**
     * 按内容哈希取消分片上传，并清理进行中映射。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     */
    default void abortUploadByHash(String bucketName, String hash) {
        Optional<MultipartUploadInfo> upload = findUploadByHash(bucketName, hash);
        if (upload.isPresent()) {
            abortMultipartUpload(bucketName, upload.get().getObjectName(),
                    upload.get().getUploadId());
            removeUploadHash(bucketName, hash);
        }
    }

    // ===================== 秒传（内容哈希） =====================

    /**
     * 判断指定内容哈希是否已存在。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值（32 位十六进制小写）
     * @return true 表示相同内容的文件已存在
     */
    boolean isContentHashExist(String bucketName, String hash);

    /**
     * 创建内容哈希标记对象。
     * <p>在特殊前缀路径下创建标记对象，用于后续的 O(1) 秒传判断。</p>
     *
     * @param bucketName   Bucket 名称
     * @param hash         MD5 内容哈希值
     * @param sourceObject 源对象路径
     */
    void createContentHashMarker(String bucketName, String hash, String sourceObject);

    /**
     * 获取哈希标记对象中记录的源对象路径。
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值
     * @return 源对象路径，若不存在则返回 empty
     */
    Optional<String> getContentHashSource(String bucketName, String hash);

    /**
     * 检查内容哈希是否已存在，并返回源对象信息。
     * <p>默认实现基于 {@link #isContentHashExist} 与 {@link #getContentHashSource}，
     * 不返回 size/etag；{@code DefaultMinioTemplate} 会返回精确值。</p>
     *
     * @param bucketName Bucket 名称
     * @param hash       MD5 内容哈希值（32 位十六进制小写）
     * @return 检查结果
     */
    default ContentCheckResult checkContent(String bucketName, String hash) {
        if (!isContentHashExist(bucketName, hash)) {
            return ContentCheckResult.notFound();
        }
        return getContentHashSource(bucketName, hash)
                .map(source -> ContentCheckResult.exists(source, -1L, null))
                .orElseGet(ContentCheckResult::notFound);
    }

    /**
     * 命中内容哈希时，把源对象复制到目标对象。
     * <p>返回 false 表示未命中、源对象与目标相同或复制失败，调用方应按正常流程上传。</p>
     *
     * @param bucketName   Bucket 名称
     * @param hash         MD5 内容哈希值
     * @param targetObject 目标对象名
     * @return true 表示服务端复制成功
     */
    default boolean copyFromHash(String bucketName, String hash, String targetObject) {
        Optional<String> source = getContentHashSource(bucketName, hash);
        if (!source.isPresent() || source.get().equals(targetObject)) {
            return false;
        }
        try {
            copyFile(bucketName, source.get(), bucketName, targetObject);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 上传文件并启用秒传能力。
     * <p>自动计算 MD5 哈希，判断内容是否已存在：
     * <ul>
     *   <li>若已存在：通过服务器端复制实现瞬间上传</li>
     *   <li>若不存在：正常上传并创建哈希标记</li>
     * </ul>
     * </p>
     *
     * @param bucketName       Bucket 名称
     * @param objectName       对象名
     * @param stream           文件流
     * @param contentType      内容类型
     * @param progressListener 进度监听器（可选）
     * @return 写入响应
     */
    ObjectWriteResponse uploadWithInstantUpload(String bucketName, String objectName,
                                                InputStream stream, String contentType,
                                                ProgressListener progressListener);

    /**
     * 上传文件并记录内容哈希（不自动判断秒传）。
     * <p>上传完成后在用户元数据中记录 MD5 哈希值。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 内容类型
     * @return 写入响应
     */
    ObjectWriteResponse uploadWithHash(String bucketName, String objectName,
                                       InputStream stream, String contentType);

    /**
     * 上传本地文件（自动分片，适用于大文件）。
     * <p>底层使用 SDK 的 {@code uploadObject}，文件分片上传失败时会自动重试单分片。</p>
     *
     * @param bucketName  Bucket 名称
     * @param objectName  对象名
     * @param filePath    本地文件路径
     * @param contentType 内容类型
     */
    void uploadFile(String bucketName, String objectName, String filePath, String contentType);

    // ===================== 其他操作 =====================

    /**
     * 创建空目录（在 MinIO 中以零字节对象表示）。
     *
     * @param bucketName Bucket 名称
     * @param objectName 目录路径（以 / 结尾）
     * @return 写入响应
     */
    ObjectWriteResponse createDir(String bucketName, String objectName);

    /**
     * 拷贝文件。
     *
     * @param sourceBucket 源 Bucket
     * @param sourceObject 源对象名
     * @param targetBucket 目标 Bucket
     * @param targetObject 目标对象名
     * @return 写入响应
     */
    ObjectWriteResponse copyFile(String sourceBucket, String sourceObject,
                                 String targetBucket, String targetObject);

    /**
     * 执行自定义 MinIO 操作。
     * <p>当模板封装的 API 无法满足需求时，可通过此方法直接操作原生 {@code MinioClient}。</p>
     *
     * @param callback 操作回调
     * @param <T>      返回值类型
     * @return 执行结果
     */
    <T> T execute(MinioCallback<T> callback);

}
