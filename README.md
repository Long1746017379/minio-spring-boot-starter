# Minio-Spring-Boot-Starter

## 设计starter

参考RedisTemplate 的设计理念

[GitHub-minio-spring-boot-starter](https://github.com/Long1746017379/minio-spring-boot-starter)


## 引入starter

```xml
<dependency>
    <groupId>com.along.boot</groupId>
    <artifactId>minio-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置文件

```properties
# 如不进行如下配置，则走starter里默认的配置
minio.enabled = true
minio.endpoint = http://192.168.253.100:19000
minio.accessKey = minioadmin
minio.secretKey = minioadmin
minio.bucket = default
```

## 业务层使用

```java
package com.dcl.common.util.minio;

import com.along.boot.minio.template.MinioTemplate;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.ObjectWriteResponse;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author along
 * @date 2025/12/27 20:37
 */
@Component
public class MinioUtil {
    private static final Logger log = LoggerFactory.getLogger(MinioUtil.class);

    private final MinioTemplate minioTemplate;

    public MinioUtil(@Lazy MinioTemplate minioTemplate) {
        this.minioTemplate = minioTemplate;
    }

    /**
     * @param bucket      存储桶
     * @param objectName  对象名
     * @param inputStream inputStream
     * @param contentType 类型
     * @return {@link String }
     */
    public String upload(String bucket, String objectName, InputStream inputStream, String contentType) {
        minioTemplate.upload(bucket, objectName, inputStream, contentType);
        return buildObjectUrl(bucket, objectName);
    }

    /**
     * 通用上传
     *
     * @param bucket     存储桶
     * @param objectName 对象名
     * @param file       文件
     * @return {@link String }
     */
    public String upload(String bucket, String objectName, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioTemplate.upload(bucket, objectName, inputStream, file.getContentType());
            return buildObjectUrl(bucket, objectName);
        } catch (IOException e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }

    private String buildObjectUrl(String bucket, String objectName) {
        return getObjectUrl(bucket, objectName);
    }

    /**
     * 获取文件流-下载
     */
    public InputStream download(String bucket, String objectName) {
        return minioTemplate.download(bucket, objectName);
    }

    /**
     * 删除
     */
    public void delete(String bucket, String objectName) {
        minioTemplate.remove(bucket, objectName);
    }

    // ******************************  Operate Bucket Start  ******************************

    /**
     * 判断Bucket是否存在 <br/>
     * true：存在<br/> false：不存在
     *
     * @param bucketName
     */
    public boolean bucketExists(String bucketName) {
        return minioTemplate.bucketExists(bucketName);
    }

    /**
     * 获得Bucket的策略
     *
     * @param bucketName
     */
    public String getBucketPolicy(String bucketName) {
        return minioTemplate.execute(minioClient ->
                minioClient.getBucketPolicy(
                    GetBucketPolicyArgs
                        .builder()
                        .bucket(bucketName)
                        .build()
        ));
    }

    /**
     * 获得所有Bucket列表
     */
    public List<Bucket> getAllBuckets() {
        return minioTemplate.getAllBuckets();
    }

    /**
     * 根据bucketName获取其相关信息
     *
     * @param bucketName
     * @return
     */
    public Optional<Bucket> getBucket(String bucketName) {
        return minioTemplate.getBucket(bucketName);
    }

    /**
     * 根据bucketName删除Bucket，true：删除成功； false：删除失败，文件或已不存在
     * @param bucketName
     * @throws Exception
     */
    public void removeBucket(String bucketName) {
        minioTemplate.removeBucket(bucketName);
    }

    // ******************************  Operate Bucket End  ******************************


    // ******************************  Operate Files Start  ******************************

    /**
     * 判断文件是否存在
     *
     * @param bucket 存储桶
     * @param objectName 文件名
     * @return
     */
    public boolean isObjectExistBak(String bucket, String objectName) {
        log.info("Checking if object exists: {}", objectName);
        return minioTemplate.execute(client -> {
            try {
                client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
                return true;
            } catch (ErrorResponseException e) {
                // 对象不存在
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    return false;
                }
                throw e;
            }
        });
    }

    /**
     * 判断文件是否存在
     *
     * @param bucketName 存储桶
     * @param objectName 文件名
     * @return
     */
    public boolean isObjectExist(String bucketName, String objectName) {
        boolean exist = true;
        try {
            minioTemplate.execute(minioClient -> minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build()));
        } catch (Exception e) {
            log.error("[Minio工具类] >>>> 判断文件是否存在, 异常：", e);
            exist = false;
        }
        return exist;
    }

    /**
     * 判断文件夹是否存在
     *
     * @param bucketName 存储桶
     * @param objectName 文件夹名称
     * @return
     */
    public boolean isFolderExist(String bucketName, String objectName) {
        boolean exist = false;
        try {
            Iterable<Result<Item>> results = minioTemplate.execute(minioClient -> minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).recursive(false).build()));
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() && objectName.equals(item.objectName())) {
                    exist = true;
                }
            }
        } catch (Exception e) {
            log.error("[Minio工具类]>>>> 判断文件夹是否存在，异常：", e);
            exist = false;
        }
        return exist;
    }

    /**
     * 根据文件前置查询文件
     *
     * @param bucketName 存储桶
     * @param prefix 前缀
     * @param recursive 是否使用递归查询
     * @return MinioItem 列表
     * @throws Exception
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
     * 断点下载
     *
     * @param bucketName 存储桶
     * @param objectName 文件名称
     * @param offset 起始字节的位置
     * @param length 要读取的长度
     * @return 二进制流
     */
    public InputStream getObject(String bucketName, String objectName, long offset, long length) throws Exception {
        return minioTemplate.execute( minioClient -> minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build()));
    }

    /**
     * 获取路径下文件列表
     *
     * @param bucketName 存储桶
     * @param prefix 文件名称
     * @param recursive 是否递归查找，false：模拟文件夹结构查找
     * @return 二进制流
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
     * 创建文件夹或目录
     *
     * @param bucketName 存储桶
     * @param objectName 目录路径
     */
    public ObjectWriteResponse createDir(String bucketName, String objectName) {
        return minioTemplate.createDir(bucketName, objectName);
    }

    /**
     * 获取文件信息, 如果抛出异常则说明文件不存在
     *
     * @param bucketName 存储桶
     * @param objectName 文件名称
     * @return {@link String }
     */
    public String getFileStatusInfo(String bucketName, String objectName) {
        return minioTemplate.execute(minioClient -> minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()).toString());
    }

    /**
     * 拷贝文件
     *
     * @param bucketName    存储桶
     * @param objectName    文件名
     * @param srcBucketName 目标存储桶
     * @param srcObjectName 目标文件名
     * @return {@link ObjectWriteResponse }
     */
    public ObjectWriteResponse copyFile(String bucketName, String objectName,
                                        String srcBucketName, String srcObjectName) {
        return minioTemplate.execute(minioClient -> minioClient.copyObject(
                CopyObjectArgs.builder()
                        .source(CopySource.builder().bucket(bucketName).object(objectName).build())
                        .bucket(srcBucketName)
                        .object(srcObjectName)
                        .build()));
    }

    /**
     * 获取文件外链
     *
     * @param bucketName 存储桶
     * @param objectName 文件名
     * @return {@link String }
     */
    public String getUrlExpiredOneHour(String bucketName, String objectName) {
        return getObjectUrl(bucketName,  objectName, 60 * 60);
    }

    /**
     * 获取文件外链
     *
     * @param bucketName 存储桶
     * @param objectName 文件名
     * @param expirySeconds    过期时间 <=7 秒 （外链有效时间（单位：秒））
     * @return url
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expirySeconds) {
        return minioTemplate.getObjectUrl(bucketName, objectName, expirySeconds);
    }

    /**
     * 获得文件外链
     *
     * @param bucketName 存储桶
     * @param objectName 文件名
     * @return url
     */
    public String getObjectUrl(String bucketName, String objectName) {
        return minioTemplate.getObjectUrl(bucketName, objectName);
    }

    /**
     * 将URLDecoder编码转成UTF8
     *
     * @param str
     * @return
     * @throws UnsupportedEncodingException
     */
    public String getUTF8ByURLDecoder(String str) throws UnsupportedEncodingException {
        String url = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        return URLDecoder.decode(url, "UTF-8");
    }

    // ******************************  Operate Files End  ******************************

}


```

