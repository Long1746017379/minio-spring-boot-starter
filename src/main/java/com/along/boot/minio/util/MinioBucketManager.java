package com.along.boot.minio.util;

import com.along.boot.minio.exception.MinioOperationException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * Bucket 管理器，提供 Bucket 的兜底创建能力。
 * <p>用于系统启动时确保默认 Bucket 存在，避免首次使用时因 Bucket 不存在而报错。</p>
 *
 * @author along
 */
public class MinioBucketManager {

    private final MinioClient client;

    public MinioBucketManager(MinioClient client) {
        this.client = client;
    }

    /**
     * 如果 Bucket 不存在则创建。
     *
     * @param bucket Bucket 名称
     * @throws MinioOperationException 创建失败时抛出
     */
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
