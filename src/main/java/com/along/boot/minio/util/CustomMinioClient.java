package com.along.boot.minio.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.minio.ListPartsResponse;
import io.minio.MinioAsyncClient;
import io.minio.ObjectWriteResponse;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MinIO SDK 原生异步 API 的分片上传客户端。
 * <p>MinIO SDK 8.5.x 将 multipart 相关方法声明为 protected，通过继承暴露这些能力，
 * 复用 SDK 自带的 SigV4 签名与 XML 解析，避免手写 S3 REST 请求。</p>
 *
 * @author along
 */
public class CustomMinioClient extends MinioAsyncClient {

    /**
     * 构造分片上传客户端。
     *
     * @param client 已配置好的异步客户端
     */
    public CustomMinioClient(MinioAsyncClient client) {
        super(client);
    }

    /**
     * 初始化分片上传。
     *
     * @param bucket      bucket 名称
     * @param object      对象名
     * @param contentType 内容类型，为空时不设置
     * @return uploadId
     * @throws Exception SDK 调用失败时抛出
     */
    public String initiateMultipartUpload(String bucket, String object, String contentType)
            throws Exception {
        Multimap<String, String> headers = ArrayListMultimap.create();
        if (contentType != null && !contentType.isEmpty()) {
            headers.put("Content-Type", contentType);
        }
        return createMultipartUploadAsync(bucket, null, object, headers, ArrayListMultimap.create())
                .get()
                .result()
                .uploadId();
    }

    /**
     * 上传单个分片。
     *
     * @param bucket     bucket 名称
     * @param object     对象名
     * @param uploadId   分片上传 ID
     * @param partNumber 分片编号（从 1 开始）
     * @param data       分片数据
     * @return UploadPartResponse
     * @throws Exception SDK 调用失败时抛出
     */
    public UploadPartResponse uploadPart(String bucket, String object, String uploadId,
                                         int partNumber, byte[] data) throws Exception {
        return uploadPartAsync(bucket, null, object, data, data.length, uploadId, partNumber,
                ArrayListMultimap.create(), ArrayListMultimap.create()).get();
    }

    /**
     * 列举已上传的分片（自动处理分页）。
     *
     * @param bucket   bucket 名称
     * @param object   对象名
     * @param uploadId 分片上传 ID
     * @return 已上传的分片列表
     * @throws Exception SDK 调用失败时抛出
     */
    public List<Part> listParts(String bucket, String object, String uploadId) throws Exception {
        List<Part> parts = new ArrayList<>();
        Integer partNumberMarker = null;

        while (true) {
            ListPartsResponse response = listPartsAsync(bucket, null, object, 1000,
                    partNumberMarker, uploadId, ArrayListMultimap.create(), ArrayListMultimap.create())
                    .get();
            parts.addAll(response.result().partList());
            if (!response.result().isTruncated()) {
                return parts;
            }
            partNumberMarker = response.result().nextPartNumberMarker();
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
     * @throws Exception SDK 调用失败时抛出
     */
    public ObjectWriteResponse completeMultipartUpload(String bucket, String object,
                                                       String uploadId, Part[] parts)
            throws Exception {
        return completeMultipartUploadAsync(bucket, null, object, uploadId, parts,
                ArrayListMultimap.create(), ArrayListMultimap.create()).get();
    }

    /**
     * 取消分片上传。
     *
     * @param bucket   bucket 名称
     * @param object   对象名
     * @param uploadId 分片上传 ID
     * @throws Exception SDK 调用失败时抛出
     */
    public void abortMultipartUpload(String bucket, String object, String uploadId)
            throws Exception {
        abortMultipartUploadAsync(bucket, null, object, uploadId,
                ArrayListMultimap.create(), ArrayListMultimap.create()).get();
    }

}
