package com.along.boot.minio.template;

import com.along.boot.minio.callback.MinioCallback;
import com.along.boot.minio.callback.ProgressListener;
import com.along.boot.minio.exception.MinioOperationException;
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
import io.minio.UploadPartResponse;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        try {
            return multipartClient.initiateMultipartUpload(bucketName, objectName);
        } catch (Exception e) {
            throw new MinioOperationException("Initiate multipart upload failed: " + objectName, e);
        }
    }

    @Override
    public UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId,
                                         int partNumber, InputStream stream, long partSize) {
        try {
            byte[] data = toByteArray(stream);
            return multipartClient.uploadPart(
                    bucketName, objectName, uploadId, partNumber, data);
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
    public ObjectWriteResponse resumeUpload(String bucketName, String objectName,
                                            InputStream stream, long objectSize, int partSize,
                                            String uploadId) {
        try {
            String effectiveUploadId = uploadId != null
                    ? uploadId
                    : initiateMultipartUpload(bucketName, objectName);

            List<Part> existingParts = listParts(bucketName, objectName, effectiveUploadId);
            Set<Integer> uploadedPartNumbers = new HashSet<>();
            List<Part> allParts = new ArrayList<>();
            for (Part part : existingParts) {
                uploadedPartNumbers.add(part.partNumber());
                allParts.add(part);
            }

            int partCount = (int) Math.ceil((double) objectSize / partSize);
            byte[] buffer = new byte[partSize];

            for (int partNumber = 1; partNumber <= partCount; partNumber++) {
                long currentPartSize = Math.min(partSize,
                        objectSize - (long) (partNumber - 1) * partSize);
                int bytesRead = readFully(stream, buffer, (int) currentPartSize);

                if (uploadedPartNumbers.contains(partNumber)) {
                    continue;
                }

                UploadPartResponse response = multipartClient.uploadPart(
                        bucketName, objectName, effectiveUploadId, partNumber,
                        copyOf(buffer, bytesRead));
                allParts.add(new Part(partNumber, response.etag()));
            }

            allParts.sort(Comparator.comparingInt(Part::partNumber));
            return completeMultipartUpload(bucketName, objectName, effectiveUploadId,
                    allParts.toArray(new Part[0]));
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
    public ObjectWriteResponse uploadWithInstantUpload(String bucketName, String objectName,
                                                       InputStream stream, String contentType,
                                                       ProgressListener progressListener) {
        try {
            byte[] fileBytes = toByteArray(stream);
            String hash = MinioHashUtil.computeMD5(fileBytes);

            Optional<String> sourceOpt = getContentHashSource(bucketName, hash);
            if (sourceOpt.isPresent()) {
                logger.info("Instant upload detected (content hash match): bucket={}, object={}",
                        bucketName, objectName);
                return copyFile(bucketName, sourceOpt.get(), bucketName, objectName);
            }

            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("content-hash", hash);

            InputStream uploadStream = new ByteArrayInputStream(fileBytes);
            if (progressListener != null) {
                uploadStream = new ProgressInputStream(uploadStream, progressListener, fileBytes.length);
            }

            ObjectWriteResponse response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(uploadStream, fileBytes.length,
                                    Math.min(fileBytes.length, 10 * 1024 * 1024))
                            .contentType(contentType)
                            .userMetadata(userMetadata)
                            .build()
            );

            createContentHashMarker(bucketName, hash, objectName);
            return response;
        } catch (Exception e) {
            throw new MinioOperationException("Upload with instant upload failed: " + objectName, e);
        }
    }

    @Override
    public ObjectWriteResponse uploadWithHash(String bucketName, String objectName,
                                              InputStream stream, String contentType) {
        try {
            byte[] fileBytes = toByteArray(stream);
            String hash = MinioHashUtil.computeMD5(fileBytes);

            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("content-hash", hash);

            return minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(fileBytes), fileBytes.length,
                                    Math.min(fileBytes.length, 10 * 1024 * 1024))
                            .contentType(contentType)
                            .userMetadata(userMetadata)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Upload with hash failed: " + objectName, e);
        }
    }

    // ===================== Helper Methods =====================

    /**
     * 将输入流完整读取为字节数组。
     */
    private byte[] toByteArray(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
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
