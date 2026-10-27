package com.along.boot.minio.util;

import com.along.boot.minio.exception.MinioOperationException;
import com.along.boot.minio.model.MultipartUploadInfo;
import io.minio.MinioAsyncClient;
import io.minio.ObjectWriteResponse;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分片上传客户端。
 * <p>底层复用 MinIO SDK 的 protected multipart API（{@link CustomMinioClient}），
 * 由 SDK 负责 SigV4 签名、URL 编码与响应解析。</p>
 *
 * @author along
 */
public class S3MultipartClient {

    private final CustomMinioClient client;

    /**
     * 构造分片上传客户端。
     *
     * @param endpoint  MinIO 地址
     * @param accessKey 访问密钥
     * @param secretKey 密钥
     * @param region    区域
     */
    public S3MultipartClient(String endpoint, String accessKey, String secretKey, String region) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        MinioAsyncClient.Builder builder = MinioAsyncClient.builder()
                .endpoint(normalizeEndpoint(endpoint))
                .credentials(accessKey, secretKey)
                .httpClient(httpClient);
        if (region != null && !region.isEmpty()) {
            builder.region(region);
        }
        this.client = new CustomMinioClient(builder.build(), accessKey, secretKey, httpClient);
    }

    /**
     * 初始化分片上传。
     *
     * @param bucket bucket 名称
     * @param object 对象名
     * @return uploadId
     */
    public String initiateMultipartUpload(String bucket, String object) {
        return initiateMultipartUpload(bucket, object, null);
    }

    /**
     * 初始化分片上传并指定 Content-Type。
     *
     * @param bucket      bucket 名称
     * @param object      对象名
     * @param contentType 内容类型，为空时不设置
     * @return uploadId
     */
    public String initiateMultipartUpload(String bucket, String object, String contentType) {
        try {
            return client.initiateMultipartUpload(bucket, object, contentType);
        } catch (Exception e) {
            throw new MinioOperationException("Initiate multipart upload failed", e);
        }
    }

    /**
     * 上传分片。
     *
     * @param bucket     bucket 名称
     * @param object     对象名
     * @param uploadId   分片上传 ID
     * @param partNumber 分片编号
     * @param data       分片数据
     * @return UploadPartResponse
     */
    public UploadPartResponse uploadPart(String bucket, String object, String uploadId,
                                         int partNumber, byte[] data) {
        try {
            return client.uploadPart(bucket, object, uploadId, partNumber, data);
        } catch (Exception e) {
            throw new MinioOperationException("Upload part failed: " + partNumber, e);
        }
    }

    /**
     * 列举已上传的分片（自动处理分页）。
     *
     * @param bucket   bucket 名称
     * @param object   对象名
     * @param uploadId 分片上传 ID
     * @return 已上传的分片列表
     */
    public List<Part> listParts(String bucket, String object, String uploadId) {
        try {
            return client.listParts(bucket, object, uploadId);
        } catch (Exception e) {
            throw new MinioOperationException("List parts failed", e);
        }
    }

    /**
     * 列举进行中的分片上传。
     *
     * @param bucket bucket 名称
     * @param prefix 对象名前缀
     * @return 进行中的上传列表
     */
    public List<MultipartUploadInfo> listMultipartUploads(String bucket, String prefix) {
        try {
            return client.listMultipartUploads(bucket, prefix);
        } catch (Exception e) {
            throw new MinioOperationException("List multipart uploads failed", e);
        }
    }

    /**
     * 完成分片上传。
     *
     * @param bucket   bucket 名称
     * @param object   对象名
     * @param uploadId 分片上传 ID
     * @param parts    分片列表
     * @return 写入响应
     */
    public ObjectWriteResponse completeMultipartUpload(String bucket, String object,
                                                       String uploadId, Part[] parts) {
        try {
            return client.completeMultipartUpload(bucket, object, uploadId, parts);
        } catch (Exception e) {
            throw new MinioOperationException("Complete multipart upload failed", e);
        }
    }

    /**
     * 取消分片上传。
     *
     * @param bucket   bucket 名称
     * @param object   对象名
     * @param uploadId 分片上传 ID
     */
    public void abortMultipartUpload(String bucket, String object, String uploadId) {
        try {
            client.abortMultipartUpload(bucket, object, uploadId);
        } catch (Exception e) {
            throw new MinioOperationException("Abort multipart upload failed", e);
        }
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

}
