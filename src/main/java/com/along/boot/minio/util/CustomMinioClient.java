package com.along.boot.minio.util;

import com.along.boot.minio.model.MultipartUploadInfo;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.minio.ListPartsResponse;
import io.minio.MinioAsyncClient;
import io.minio.ObjectWriteResponse;
import io.minio.Signer;
import io.minio.UploadPartResponse;
import io.minio.credentials.Credentials;
import io.minio.http.Method;
import io.minio.messages.Part;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MinIO SDK 原生异步 API 的分片上传客户端。
 * <p>MinIO SDK 8.5.x 将 multipart 相关方法声明为 protected，通过继承暴露这些能力。
 * 分片操作复用 SDK 的签名与 XML 解析；ListMultipartUploads 因 SDK 响应模型要求
 * StorageClass 非空，复用 SDK 的 URL 构建与签名后自行解析 XML。</p>
 *
 * @author along
 */
public class CustomMinioClient extends MinioAsyncClient {

    private final String accessKey;

    private final String secretKey;

    private final OkHttpClient httpClient;

    /**
     * 构造分片上传客户端。
     *
     * @param client 已配置好的异步客户端
     */
    public CustomMinioClient(MinioAsyncClient client) {
        this(client, null, null, null);
    }

    /**
     * 构造分片上传客户端。
     *
     * @param client     已配置好的异步客户端
     * @param accessKey  访问密钥
     * @param secretKey  密钥
     * @param httpClient HTTP 客户端
     */
    public CustomMinioClient(MinioAsyncClient client, String accessKey, String secretKey,
                             OkHttpClient httpClient) {
        super(client);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.httpClient = httpClient;
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
     * 列举进行中的分片上传（自动处理分页）。
     * <p>SDK 8.5.x 的 ListMultipartUploads 响应模型要求 StorageClass 非空，
     * 而部分 MinIO 版本返回空值，故这里复用 SDK 的 URL 构建与签名，自行解析 XML。</p>
     *
     * @param bucket bucket 名称
     * @param prefix 对象名前缀
     * @return 进行中的上传列表
     * @throws Exception 请求或解析失败时抛出
     */
    public List<MultipartUploadInfo> listMultipartUploads(String bucket, String prefix)
            throws Exception {
        if (accessKey == null || secretKey == null || httpClient == null) {
            throw new IllegalStateException(
                    "accessKey, secretKey and httpClient are required for listMultipartUploads");
        }
        Credentials credentials = new Credentials(accessKey, secretKey, null, null);
        List<MultipartUploadInfo> uploads = new ArrayList<>();
        String keyMarker = null;
        String uploadIdMarker = null;

        while (true) {
            Multimap<String, String> queryParams = ArrayListMultimap.create();
            queryParams.put("uploads", "");
            queryParams.put("max-uploads", "1000");
            if (prefix != null && !prefix.isEmpty()) {
                queryParams.put("prefix", prefix);
            }
            if (keyMarker != null) {
                queryParams.put("key-marker", keyMarker);
            }
            if (uploadIdMarker != null) {
                queryParams.put("upload-id-marker", uploadIdMarker);
            }

            String region = getRegionAsync(bucket, null).get();
            HttpUrl url = buildUrl(Method.GET, bucket, null, region, queryParams);
            Request request = createRequest(url, Method.GET, null, null, 0, credentials);
            HttpUrl signedUrl = Signer.presignV4(request, region, accessKey, secretKey, 3600);

            Request signedRequest = new Request.Builder().url(signedUrl).get().build();
            try (Response response = httpClient.newCall(signedRequest).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IOException("List multipart uploads failed, status: "
                            + response.code() + " " + body);
                }
                String xml = response.body() != null ? response.body().string() : "";
                RawListUploads page = parseListUploads(xml);
                uploads.addAll(page.uploads);
                if (!page.truncated) {
                    return uploads;
                }
                if (page.nextKeyMarker == null && page.nextUploadIdMarker == null) {
                    return uploads;
                }
                keyMarker = page.nextKeyMarker;
                uploadIdMarker = page.nextUploadIdMarker;
            }
        }
    }

    private RawListUploads parseListUploads(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        RawListUploads result = new RawListUploads();
        NodeList uploadNodes = document.getElementsByTagName("Upload");
        for (int i = 0; i < uploadNodes.getLength(); i++) {
            Element upload = (Element) uploadNodes.item(i);
            String key = text(upload, "Key");
            String uploadId = text(upload, "UploadId");
            if (key == null || uploadId == null) {
                continue;
            }
            String initiatedText = text(upload, "Initiated");
            ZonedDateTime initiated = null;
            if (initiatedText != null && !initiatedText.isEmpty()) {
                try {
                    initiated = OffsetDateTime.parse(initiatedText).toZonedDateTime();
                } catch (DateTimeParseException ignored) {
                    // 时间解析失败不影响 uploadId 查找
                }
            }
            result.uploads.add(new MultipartUploadInfo(uploadId, key, initiated));
        }

        Element root = document.getDocumentElement();
        result.truncated = "true".equalsIgnoreCase(text(root, "IsTruncated"));
        result.nextKeyMarker = text(root, "NextKeyMarker");
        result.nextUploadIdMarker = text(root, "NextUploadIdMarker");
        return result;
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    private static class RawListUploads {

        private final List<MultipartUploadInfo> uploads = new ArrayList<>();

        private boolean truncated;

        private String nextKeyMarker;

        private String nextUploadIdMarker;

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
