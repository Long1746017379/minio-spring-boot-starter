package com.along.boot.minio.template;

import com.along.boot.minio.callback.MinioCallback;
import com.along.boot.minio.exception.MinioOperationException;
import com.along.boot.minio.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author along
 * @date 2025/12/27 11:31
 */
public class DefaultMinioTemplate implements MinioTemplate {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public DefaultMinioTemplate(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 判断Bucket是否存在 <br/>
     * true：存在 <br/>
     * false：不存在
     *
     * @param bucketName
     * @return boolean
     */
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

    /**
     * 安全模式创建桶
     *
     * @param bucketName
     */
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

    /* ===================== 上传 ===================== */

    @Override
    public void upload(String objectName, InputStream stream, String contentType) {
        upload(properties.getBucket(), objectName, stream, contentType);
    }

    @Override
    public void upload(String bucketName, String objectName,
                       InputStream stream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, -1, 10 * 1024 * 1024)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Upload failed: " + objectName, e);
        }
    }

    /* ===================== 下载 ===================== */

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

    /**
     * 获得所有Bucket列表
     *
     * @return
     * @throws Exception
     */
    @Override
    public List<Bucket> getAllBuckets() {
        try {
            return minioClient.listBuckets();
        } catch (Exception e) {
            throw new MinioOperationException("getAllBuckets Exception ", e);
        }
    }

    /**
     * 根据bucketName获取其相关信息
     *
     * @param bucketName
     * @return
     * @throws Exception
     */
    @Override
    public Optional<Bucket> getBucket(String bucketName) {
        try {
            return getAllBuckets().stream().filter(b -> b.name().equals(bucketName)).findFirst();
        } catch (Exception e) {
            throw new MinioOperationException("getBucket: " + bucketName, e);
        }
    }

    /* ===================== 删除 ===================== */

    /**
     * 根据bucketName删除Bucket <br/>
     * true：删除成功； <br/>
     * false：删除失败，文件或已不存在 <br/>
     *
     * @param bucketName
     * @throws Exception
     */
    @Override
    public void removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new MinioOperationException("Remove Bucket: " + bucketName, e);
        }
    }

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
    public List<String> listObjects(String bucketName) {
        try {
            List<String> objects = new ArrayList<>();

            Iterable<Result<Item>> results =
                    minioClient.listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(bucketName)
                                    .build()
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

    /**
     * 创建文件夹或目录
     *
     * @param bucketName 存储桶
     * @param objectName 目录路径
     */
    @Override
    public ObjectWriteResponse createDir(String bucketName, String objectName)  {
        try {
            return minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .build());
        } catch (Exception e) {
            throw new MinioOperationException("Minio createDir failed", e);
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

}
