package com.along.boot.minio.template;

import com.along.boot.minio.callback.MinioCallback;
import com.along.boot.minio.callback.ProgressListener;
import com.along.boot.minio.exception.MinioOperationException;
import com.along.boot.minio.model.ContentCheckResult;
import com.along.boot.minio.model.MultipartUploadInfo;
import com.along.boot.minio.properties.MinioProperties;
import com.along.boot.minio.util.MinioHashUtil;
import com.along.boot.minio.util.S3MultipartClient;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.UploadPartResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link MinioTemplate} 的默认实现，基于 MinIO Java SDK 封装。
 * <p>所有异常统一包装为 {@link MinioOperationException} 运行时异常抛出。</p>
 *
 * @author along
 */
public class DefaultMinioTemplate implements MinioTemplate {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMinioTemplate.class);

    private static final String HASH_PREFIX = ".__minio_hash/";

    private static final String UPLOAD_HASH_PREFIX = ".__minio_uploads/";

    private static final int MIN_PART_SIZE = 5 * 1024 * 1024;

    private static final int MAX_PART_COUNT = 10000;

    private final MinioClient minioClient;

    private final S3MultipartClient multipartClient;

    private final MinioProperties properties;

    public DefaultMinioTemplate(MinioClient minioClient, MinioProperties properties,
                                S3MultipartClient multipartClient) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.multipartClient = multipartClient;
    }

    // ===================== Bucket 操作 =====================

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Check bucket failed: " + bucketName, e);
        }
    }

    @Override
    public void createBucketIfAbsent(String bucketName) {
        try {
            if (!bucketExists(bucketName)) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
            }
        } catch (Exception e) {
            throw new MinioOperationException("Create bucket failed: " + bucketName, e);
        }
    }

    @Override
    public List<Bucket> getAllBuckets() {
        try {
            return minioClient.listBuckets();
        } catch (Exception e) {
            throw new MinioOperationException("getAllBuckets Exception ", e);
        }
    }

    @Override
    public Optional<Bucket> getBucket(String bucketName) {
        try {
            if (!bucketExists(bucketName)) {
                return Optional.empty();
            }
            return minioClient.listBuckets().stream()
                    .filter(b -> b.name().equals(bucketName))
                    .findFirst();
        } catch (Exception e) {
            throw new MinioOperationException("getBucket: " + bucketName, e);
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new MinioOperationException("Remove Bucket: " + bucketName, e);
        }
    }

    // ===================== 上传 =====================

    @Override
    public void upload(String objectName, InputStream stream, String contentType) {
        upload(properties.getBucket(), objectName, stream, contentType);
    }

    @Override
    public void upload(String bucketName, String objectName,
                       InputStream stream, String contentType) {
        try {
            logger.info("Uploading object: bucket={}, object={}", bucketName, objectName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, -1, 10 * 1024 * 1024)
                            .contentType(contentType)
                            .build()
            );
            logger.info("Upload completed: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            logger.error("Upload failed: bucket={}, object={}", bucketName, objectName, e);
            throw new MinioOperationException("Upload failed: " + objectName, e);
        }
    }

    @Override
    public void upload(String bucketName, String objectName, InputStream stream,
                       String contentType, ProgressListener progressListener) {
        try {
            InputStream wrapped = new ProgressInputStream(stream, progressListener);
            logger.info("Uploading object with progress: bucket={}, object={}", bucketName, objectName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(wrapped, -1, 10 * 1024 * 1024)
                            .contentType(contentType)
                            .build()
            );
            logger.info("Upload completed: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            logger.error("Upload failed: bucket={}, object={}", bucketName, objectName, e);
            throw new MinioOperationException("Upload failed: " + objectName, e);
        }
    }

    @Override
    public void upload(String bucketName, String objectName, InputStream stream,
                       String contentType, long objectSize, int partSize) {
        try {
            logger.info("Uploading object (multipart): bucket={}, object={}, size={}, partSize={}",
                    bucketName, objectName, objectSize, partSize);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, objectSize, partSize)
                            .contentType(contentType)
                            .build()
            );
            logger.info("Upload completed: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            logger.error("Upload failed: bucket={}, object={}", bucketName, objectName, e);
            throw new MinioOperationException("Upload failed: " + objectName, e);
        }
    }

    // ===================== 下载 =====================

    @Override
    public InputStream download(String objectName) {
        return download(properties.getBucket(), objectName);
    }

    @Override
    public InputStream download(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Download failed: " + objectName, e);
        }
    }

    // ===================== 删除 =====================

    @Override
    public void remove(String objectName) {
        remove(properties.getBucket(), objectName);
    }

    @Override
    public void remove(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Remove failed: " + objectName, e);
        }
    }

    @Override
    public void remove(String bucketName, List<String> objectNames) {
        try {
            List<DeleteObject> objects = objectNames.stream()
                    .map(DeleteObject::new)
                    .collect(Collectors.toList());
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(bucketName).objects(objects).build()
            );
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                logger.warn("Failed to delete object: {}", error.objectName());
            }
        } catch (Exception e) {
            throw new MinioOperationException("Batch remove failed", e);
        }
    }

    // ===================== 查询 =====================

    @Override
    public List<String> listObjects(String bucketName) {
        try {
            List<String> objects = new ArrayList<>();
            Iterable<Result<Item>> results =
                    minioClient.listObjects(
                            ListObjectsArgs.builder().bucket(bucketName).build()
                    );
            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(item.objectName());
            }
            return objects;
        } catch (Exception e) {
            throw new MinioOperationException("List objects failed: " + bucketName, e);
        }
    }

    @Override
    public StatObjectResponse getObjectInfo(String bucketName, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Get object info failed: " + objectName, e);
        }
    }

    @Override
    public boolean isObjectExist(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== 预签名 URL =====================

    @Override
    public String getObjectUrl(String bucketName, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Get object url failed", e);
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Get object url failed", e);
        }
    }

    @Override
    public List<String> getObjectUrls(String bucketName, List<String> objectNames, Integer expirySeconds) {
        return objectNames.stream()
                .map(name -> getObjectUrl(bucketName, name, expirySeconds))
                .collect(Collectors.toList());
    }

    // ===================== 桶策略 =====================

    @Override
    public String getBucketPolicy(String bucketName) {
        try {
            return minioClient.getBucketPolicy(
                    GetBucketPolicyArgs.builder().bucket(bucketName).build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Get bucket policy failed: " + bucketName, e);
        }
    }

    @Override
    public void setBucketPolicy(String bucketName, String policy) {
        try {
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Set bucket policy failed: " + bucketName, e);
        }
    }

    // ===================== 分片上传（文件） =====================

    @Override
    public void uploadFile(String bucketName, String objectName, String filePath, String contentType) {
        try {
            logger.info("Uploading file (multipart): bucket={}, object={}, file={}", bucketName, objectName, filePath);
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .filename(filePath)
                            .contentType(contentType)
                            .build()
            );
            logger.info("Upload file completed: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            logger.error("Upload file failed: bucket={}, object={}", bucketName, objectName, e);
            throw new MinioOperationException("Upload file failed: " + objectName, e);
        }
    }

    // ===================== 其他操作 =====================

    @Override
    public ObjectWriteResponse createDir(String bucketName, String objectName) {
        try {
            return minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Minio createDir failed", e);
        }
    }

    @Override
    public ObjectWriteResponse copyFile(String sourceBucket, String sourceObject,
                                        String targetBucket, String targetObject) {
        try {
            return minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .source(CopySource.builder().bucket(sourceBucket).object(sourceObject).build())
                            .bucket(targetBucket)
                            .object(targetObject)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Copy file failed", e);
        }
    }

    @Override
    public <T> T execute(MinioCallback<T> callback) {
        try {
            return callback.doInMinio(minioClient);
        } catch (Exception e) {
            throw new MinioOperationException("Minio execute failed", e);
        }
    }

    // ===================== 分片上传（断点续传） =====================

    @Override
    public String initiateMultipartUpload(String bucketName, String objectName) {
        return initiateMultipartUpload(bucketName, objectName, null);
    }

    @Override
    public String initiateMultipartUpload(String bucketName, String objectName, String contentType) {
        try {
            return multipartClient.initiateMultipartUpload(bucketName, objectName, contentType);
        } catch (Exception e) {
            throw new MinioOperationException("Initiate multipart upload failed: " + objectName, e);
        }
    }

    @Override
    public UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId,
                                         int partNumber, InputStream stream, long partSize) {
        if (partSize <= 0 || partSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("partSize must be between 1 and "
                    + Integer.MAX_VALUE + " bytes");
        }
        try {
            byte[] buffer = new byte[(int) partSize];
            int bytesRead = readFully(stream, buffer, buffer.length);
            if (bytesRead != buffer.length) {
                throw new MinioOperationException("Unexpected end of stream while reading part "
                        + partNumber + ": expected " + partSize + " bytes, got " + bytesRead,
                        new IOException("Unexpected end of stream"));
            }
            return multipartClient.uploadPart(
                    bucketName, objectName, uploadId, partNumber, buffer);
        } catch (MinioOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new MinioOperationException("Upload part failed: " + objectName
                    + ", part=" + partNumber, e);
        }
    }

    @Override
    public List<Part> listParts(String bucketName, String objectName, String uploadId) {
        try {
            return multipartClient.listParts(bucketName, objectName, uploadId);
        } catch (Exception e) {
            throw new MinioOperationException("List parts failed: " + objectName
                    + ", uploadId=" + uploadId, e);
        }
    }

    @Override
    public ObjectWriteResponse completeMultipartUpload(String bucketName, String objectName,
                                                       String uploadId, Part[] parts) {
        try {
            return multipartClient.completeMultipartUpload(
                    bucketName, objectName, uploadId, parts);
        } catch (Exception e) {
            throw new MinioOperationException("Complete multipart upload failed: " + objectName, e);
        }
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            multipartClient.abortMultipartUpload(bucketName, objectName, uploadId);
        } catch (Exception e) {
            throw new MinioOperationException("Abort multipart upload failed: " + objectName, e);
        }
    }

    @Override
    public List<MultipartUploadInfo> listMultipartUploads(String bucketName, String prefix) {
        try {
            return multipartClient.listMultipartUploads(bucketName, prefix);
        } catch (Exception e) {
            throw new MinioOperationException("List multipart uploads failed: " + prefix, e);
        }
    }

    @Override
    public void registerUploadHash(String bucketName, String hash, String objectName,
                                   String uploadId) {
        try {
            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("upload-id", uploadId);
            userMetadata.put("object", objectName);
            userMetadata.put("content-hash", hash);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(UPLOAD_HASH_PREFIX + hash)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .userMetadata(userMetadata)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Register upload hash failed: " + hash, e);
        }
    }

    @Override
    public void removeUploadHash(String bucketName, String hash) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(UPLOAD_HASH_PREFIX + hash)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Remove upload hash failed: " + hash, e);
        }
    }

    @Override
    public Optional<MultipartUploadInfo> findUploadByHash(String bucketName, String hash) {
        try {
            StatObjectResponse marker = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(UPLOAD_HASH_PREFIX + hash)
                            .build()
            );
            Map<String, String> metadata = marker.userMetadata();
            String uploadId = metadata == null ? null : metadata.get("upload-id");
            String objectName = metadata == null ? null : metadata.get("object");
            if (uploadId == null || uploadId.isEmpty()
                    || objectName == null || objectName.isEmpty()) {
                logger.warn("Upload hash marker is incomplete: bucket={}, hash={}",
                        bucketName, hash);
                return Optional.empty();
            }

            try {
                listParts(bucketName, objectName, uploadId);
                return Optional.of(new MultipartUploadInfo(uploadId, objectName, null));
            } catch (MinioOperationException e) {
                if (isNoSuchUpload(e)) {
                    removeUploadHash(bucketName, hash);
                    return Optional.empty();
                }
                throw e;
            }
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new MinioOperationException("Find upload by hash failed: " + hash, e);
        } catch (Exception e) {
            throw new MinioOperationException("Find upload by hash failed: " + hash, e);
        }
    }

    private boolean isNoSuchUpload(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ErrorResponseException) {
                String code = ((ErrorResponseException) cause).errorResponse().code();
                if ("NoSuchUpload".equals(code)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public ObjectWriteResponse resumeUpload(String bucketName, String objectName,
                                            InputStream stream, long objectSize, int partSize,
                                            String uploadId) {
        return resumeUpload(bucketName, objectName, stream, objectSize, partSize, uploadId, null);
    }

    @Override
    public ObjectWriteResponse resumeUpload(String bucketName, String objectName,
                                            InputStream stream, long objectSize, int partSize,
                                            String uploadId, String contentType) {
        if (objectSize < 0) {
            throw new IllegalArgumentException("objectSize must be >= 0 for resume upload");
        }
        if (objectSize > 0) {
            validatePartSize(objectSize, partSize);
        }
        try {
            if (objectSize == 0) {
                PutObjectArgs.Builder builder = PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1);
                if (contentType != null && !contentType.isEmpty()) {
                    builder.contentType(contentType);
                }
                return minioClient.putObject(builder.build());
            }

            String effectiveUploadId = uploadId != null
                    ? uploadId
                    : initiateMultipartUpload(bucketName, objectName, contentType);

            List<Part> existingParts = listParts(bucketName, objectName, effectiveUploadId);
            Set<Integer> uploadedPartNumbers = new HashSet<>();
            List<Part> allParts = new ArrayList<>();
            for (Part part : existingParts) {
                uploadedPartNumbers.add(part.partNumber());
                allParts.add(part);
            }

            int partCount = (int) Math.ceil((double) objectSize / partSize);
            validateExistingParts(existingParts, objectSize, partSize, partCount);

            byte[] buffer = new byte[partSize];
            for (int partNumber = 1; partNumber <= partCount; partNumber++) {
                long currentPartSize = Math.min(partSize,
                        objectSize - (long) (partNumber - 1) * partSize);

                if (uploadedPartNumbers.contains(partNumber)) {
                    skipFully(stream, currentPartSize);
                    continue;
                }

                int bytesRead = readFully(stream, buffer, (int) currentPartSize);
                if (bytesRead != currentPartSize) {
                    throw new MinioOperationException("Unexpected end of stream at part " + partNumber
                            + ": expected " + currentPartSize + " bytes, got " + bytesRead,
                            new IOException("Unexpected end of stream"));
                }

                UploadPartResponse response = multipartClient.uploadPart(
                        bucketName, objectName, effectiveUploadId, partNumber,
                        copyOf(buffer, bytesRead));
                allParts.add(new Part(partNumber, response.etag()));
            }

            allParts.sort(Comparator.comparingInt(Part::partNumber));
            return completeMultipartUpload(bucketName, objectName, effectiveUploadId,
                    allParts.toArray(new Part[0]));
        } catch (MinioOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new MinioOperationException("Resume upload failed: " + objectName, e);
        }
    }

    // ===================== 秒传（内容哈希） =====================

    @Override
    public boolean isContentHashExist(String bucketName, String hash) {
        return isObjectExist(bucketName, HASH_PREFIX + hash);
    }

    @Override
    public void createContentHashMarker(String bucketName, String hash, String sourceObject) {
        try {
            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("content-hash", hash);
            userMetadata.put("source-object", sourceObject);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(HASH_PREFIX + hash)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .userMetadata(userMetadata)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Create content hash marker failed: " + hash, e);
        }
    }

    @Override
    public Optional<String> getContentHashSource(String bucketName, String hash) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(HASH_PREFIX + hash)
                            .build()
            );
            Map<String, String> metadata = stat.userMetadata();
            if (metadata != null) {
                String source = metadata.get("source-object");
                if (source != null && !source.isEmpty()) {
                    return Optional.of(source);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public ContentCheckResult checkContent(String bucketName, String hash) {
        try {
            StatObjectResponse marker = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(HASH_PREFIX + hash)
                            .build()
            );
            Map<String, String> metadata = marker.userMetadata();
            String source = metadata == null ? null : metadata.get("source-object");
            if (source == null || source.isEmpty()) {
                logger.warn("Content hash marker has no source object: bucket={}, hash={}",
                        bucketName, hash);
                return ContentCheckResult.notFound();
            }

            try {
                StatObjectResponse sourceStat = minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(bucketName)
                                .object(source)
                                .build()
                );
                return ContentCheckResult.exists(source, sourceStat.size(), sourceStat.etag());
            } catch (ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    return ContentCheckResult.stale(source);
                }
                throw e;
            }
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return ContentCheckResult.notFound();
            }
            throw new MinioOperationException("Check content failed: " + hash, e);
        } catch (Exception e) {
            throw new MinioOperationException("Check content failed: " + hash, e);
        }
    }

    @Override
    public boolean copyFromHash(String bucketName, String hash, String targetObject) {
        Optional<String> source = getContentHashSource(bucketName, hash);
        if (!source.isPresent() || source.get().equals(targetObject)) {
            return false;
        }
        try {
            copyFile(bucketName, source.get(), bucketName, targetObject);
            return true;
        } catch (MinioOperationException e) {
            logger.warn("Copy from content hash failed, caller should upload: "
                    + "bucket={}, hash={}, target={}", bucketName, hash, targetObject, e);
            return false;
        }
    }

    @Override
    public ObjectWriteResponse uploadWithInstantUpload(String bucketName, String objectName,
                                                       InputStream stream, String contentType,
                                                       ProgressListener progressListener) {
        SpooledContent content = null;
        try {
            content = spoolWithMD5(stream);

            Optional<String> sourceOpt = getContentHashSource(bucketName, content.hash);
            if (sourceOpt.isPresent() && !sourceOpt.get().equals(objectName)) {
                try {
                    logger.info("Instant upload detected (content hash match): bucket={}, object={}",
                            bucketName, objectName);
                    return copyFile(bucketName, sourceOpt.get(), bucketName, objectName);
                } catch (MinioOperationException copyFailure) {
                    logger.warn("Content hash marker points to an unavailable object, fallback to upload: "
                            + "bucket={}, hash={}", bucketName, content.hash);
                }
            }

            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("content-hash", content.hash);

            long size = Files.size(content.path);
            try (InputStream fileStream = Files.newInputStream(content.path)) {
                InputStream uploadStream = progressListener != null
                        ? new ProgressInputStream(fileStream, progressListener, size)
                        : fileStream;

                PutObjectArgs.Builder builder = PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(uploadStream, size, -1)
                        .userMetadata(userMetadata);
                if (contentType != null && !contentType.isEmpty()) {
                    builder.contentType(contentType);
                }

                ObjectWriteResponse response = minioClient.putObject(builder.build());
                createContentHashMarker(bucketName, content.hash, objectName);
                return response;
            }
        } catch (Exception e) {
            throw new MinioOperationException("Upload with instant upload failed: " + objectName, e);
        } finally {
            deleteQuietly(content);
        }
    }

    @Override
    public ObjectWriteResponse uploadWithHash(String bucketName, String objectName,
                                              InputStream stream, String contentType) {
        SpooledContent content = null;
        try {
            content = spoolWithMD5(stream);

            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("content-hash", content.hash);

            long size = Files.size(content.path);
            try (InputStream fileStream = Files.newInputStream(content.path)) {
                PutObjectArgs.Builder builder = PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(fileStream, size, -1)
                        .userMetadata(userMetadata);
                if (contentType != null && !contentType.isEmpty()) {
                    builder.contentType(contentType);
                }
                return minioClient.putObject(builder.build());
            }
        } catch (Exception e) {
            throw new MinioOperationException("Upload with hash failed: " + objectName, e);
        } finally {
            deleteQuietly(content);
        }
    }

    // ===================== Helper Methods =====================

    /**
     * 将输入流写入临时文件，同时计算 MD5，避免大文件全量驻留内存。
     */
    private SpooledContent spoolWithMD5(InputStream stream) throws IOException {
        Path tempFile = Files.createTempFile("minio-upload-", ".tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Files.deleteIfExists(tempFile);
            throw new IOException("MD5 algorithm not available", e);
        }

        try (DigestOutputStream out = new DigestOutputStream(
                Files.newOutputStream(tempFile), digest)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }

        return new SpooledContent(tempFile, MinioHashUtil.toHex(digest.digest()));
    }

    /**
     * 删除临时文件，失败时仅记录日志。
     */
    private void deleteQuietly(SpooledContent content) {
        if (content == null) {
            return;
        }
        try {
            Files.deleteIfExists(content.path);
        } catch (IOException e) {
            logger.warn("Failed to delete temporary upload file: {}", content.path, e);
        }
    }

    /**
     * 校验分片大小与分片数量是否符合 S3 约束。
     */
    private void validatePartSize(long objectSize, int partSize) {
        if (partSize < MIN_PART_SIZE) {
            throw new IllegalArgumentException("partSize must be at least 5MiB");
        }
        long partCount = (long) Math.ceil((double) objectSize / partSize);
        if (partCount > MAX_PART_COUNT) {
            throw new IllegalArgumentException("partCount " + partCount
                    + " exceeds the maximum of " + MAX_PART_COUNT);
        }
    }

    /**
     * 校验已有分片是否与本次续传的分片大小一致，避免合并出损坏对象。
     */
    private void validateExistingParts(List<Part> existingParts, long objectSize,
                                       int partSize, int partCount) {
        for (Part part : existingParts) {
            if (part.partNumber() > partCount) {
                throw new MinioOperationException("Existing part " + part.partNumber()
                        + " is out of range for partCount " + partCount,
                        new IllegalStateException("Inconsistent multipart upload"));
            }
            long existingSize = part.partSize();
            if (existingSize <= 0) {
                continue;
            }
            long expected = part.partNumber() < partCount
                    ? partSize
                    : objectSize - (long) (partCount - 1) * partSize;
            if (existingSize != expected) {
                throw new MinioOperationException("Existing part " + part.partNumber()
                        + " has size " + existingSize + " but " + expected
                        + " is required; refuse to resume with a different part size",
                        new IllegalStateException("Inconsistent part size"));
            }
        }
    }

    /**
     * 跳过指定长度的字节；底层不支持 skip 时退化为逐字节读取。
     */
    private void skipFully(InputStream in, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (in.read() == -1) {
                throw new IOException("Unexpected end of stream while skipping " + length + " bytes");
            }
            remaining--;
        }
    }

    /**
     * 从输入流中精确读取指定长度的字节。
     */
    private int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }

    /**
     * 复制字节数组的前 length 个字节。
     */
    private byte[] copyOf(byte[] original, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(original, 0, copy, 0, length);
        return copy;
    }

    /**
     * 临时文件与内容哈希的持有对象。
     */
    private static class SpooledContent {

        private final Path path;

        private final String hash;

        private SpooledContent(Path path, String hash) {
            this.path = path;
            this.hash = hash;
        }

    }

    /**
     * 带进度监听的输入流包装器。
     * <p>包装原始 InputStream，在每次读取操作后回调 {@link ProgressListener} 报告进度。</p>
     */
    private static class ProgressInputStream extends InputStream {

        private final InputStream delegate;

        private final ProgressListener listener;

        private long bytesRead;

        private final long totalBytes;

        /**
         * 构造进度流。
         *
         * @param delegate 原始输入流
         * @param listener 进度监听器
         */
        ProgressInputStream(InputStream delegate, ProgressListener listener) {
            this(delegate, listener, -1);
        }

        /**
         * 构造进度流（指定总大小）。
         *
         * @param delegate   原始输入流
         * @param listener   进度监听器
         * @param totalBytes 总字节数（-1 表示未知）
         */
        ProgressInputStream(InputStream delegate, ProgressListener listener, long totalBytes) {
            this.delegate = delegate;
            this.listener = listener;
            this.totalBytes = totalBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                bytesRead++;
                fireProgress();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                bytesRead += n;
                fireProgress();
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = delegate.skip(n);
            bytesRead += skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void fireProgress() {
            if (listener != null) {
                listener.onProgress(bytesRead, totalBytes < 0 ? bytesRead : totalBytes);
            }
        }

    }

}
