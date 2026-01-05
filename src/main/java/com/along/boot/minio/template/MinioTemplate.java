package com.along.boot.minio.template;

import com.along.boot.minio.callback.MinioCallback;
import io.minio.ObjectWriteResponse;
import io.minio.messages.Bucket;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * @author along
 * @date 2025/12/27 11:30
 */
public interface MinioTemplate {

    boolean bucketExists(String bucketName);

    void createBucketIfAbsent(String bucketName);

    void upload(String objectName, InputStream stream, String contentType);

    void upload(String bucketName, String objectName, InputStream stream, String contentType);

    InputStream download(String objectName);

    InputStream download(String bucketName, String objectName);

    List<Bucket> getAllBuckets();

    Optional<Bucket> getBucket(String bucketName);

    void removeBucket(String bucketName);

    void remove(String objectName);

    void remove(String bucketName, String objectName);

    List<String> listObjects(String bucketName);

    String getObjectUrl(String bucketName, String objectName);

    String getObjectUrl(String bucketName, String objectName, Integer expirySeconds);

    ObjectWriteResponse createDir(String bucketName, String objectName);

    <T> T execute(MinioCallback<T> callback);

}
