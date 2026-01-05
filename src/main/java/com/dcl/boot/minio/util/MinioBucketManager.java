package com.dcl.boot.minio.util;

import com.dcl.boot.minio.exception.MinioOperationException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * MinioBucketManager
 *
 * @author along
 * @date 2025/12/22 23:35
 */
public class MinioBucketManager {

    private final MinioClient client;

    public MinioBucketManager(MinioClient client) {
        this.client = client;
    }

    public void createIfAbsent(String bucket) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build()
                );
            }
        } catch (Exception e) {
            throw new MinioOperationException("Create bucket failed: " + bucket, e);
        }
    }
}
