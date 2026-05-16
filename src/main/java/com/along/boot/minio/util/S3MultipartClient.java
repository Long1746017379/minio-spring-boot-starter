package com.along.boot.minio.util;

import com.along.boot.minio.exception.MinioOperationException;
import io.minio.ObjectWriteResponse;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * S3 分片上传客户端，通过直接调用 S3 REST API 实现手动分片上传。
 * <p>MinIO Java SDK 8.5.7 未公开 multipart 操作，故通过此客户端
 * 使用 OkHttp + AWS Signature V4 直接发起 HTTP 请求。</p>
 *
 * @author along
 */
public class S3MultipartClient {

    private static final MediaType XML_MEDIA_TYPE = MediaType.parse("application/xml");
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final OkHttpClient httpClient;
    private final S3Signer signer;
    private final String endpoint;
    private final String region;

    public S3MultipartClient(String endpoint, String accessKey, String secretKey, String region) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.region = region;
        this.signer = new S3Signer(accessKey, secretKey, region);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 初始化分片上传。
     *
     * @param bucket bucket 名称
     * @param object 对象名
     * @return uploadId
     */
    public String initiateMultipartUpload(String bucket, String object) {
        String uri = uri(bucket, object);
        String query = "uploads";
        String host = extractHost();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        String amzDate = signer.generateAmzDate();
        headers.put("x-amz-date", amzDate);
        String payloadHash = S3Signer.emptyPayloadHash();
        headers.put("x-amz-content-sha256", payloadHash);

        String auth = signer.sign("POST", uri, query, headers, payloadHash, amzDate);
        headers.put("Authorization", auth);

        String url = this.endpoint + uri + "?" + query;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .headers(okhttp3.Headers.of(flattenHeaders(headers)))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new MinioOperationException("Initiate multipart upload failed, status: "
                        + response.code() + " " + body, new RuntimeException(body));
            }
            String xml = response.body() != null ? response.body().string() : "";
            return parseUploadId(xml);
        } catch (MinioOperationException e) {
            throw e;
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
        String uri = uri(bucket, object);
        String query = "partNumber=" + partNumber + "&uploadId=" + uploadId;
        String host = extractHost();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        String amzDate = signer.generateAmzDate();
        headers.put("x-amz-date", amzDate);
        String payloadHash = S3Signer.sha256Hex(data);
        headers.put("x-amz-content-sha256", payloadHash);

        String auth = signer.sign("PUT", uri, query, headers, payloadHash, amzDate);
        headers.put("Authorization", auth);

        String url = this.endpoint + uri + "?" + query;
        Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(data, OCTET_STREAM))
                .headers(okhttp3.Headers.of(flattenHeaders(headers)))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new MinioOperationException("Upload part failed, part=" + partNumber
                        + ", status=" + response.code() + " " + body, new RuntimeException(body));
            }
            String etag = response.header("ETag");
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }
            okhttp3.Headers respHeaders = response.headers();
            return new UploadPartResponse(respHeaders, bucket, region, object, uploadId, partNumber, etag);
        } catch (MinioOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new MinioOperationException("Upload part failed: " + partNumber, e);
        }
    }

    /**
     * 列举已上传的分片（自动处理分页）。
     */
    public List<Part> listParts(String bucket, String object, String uploadId) {
        List<Part> allParts = new ArrayList<>();
        Integer partNumberMarker = null;

        while (true) {
            String uri = uri(bucket, object);
            StringBuilder queryBuilder = new StringBuilder("uploadId=").append(uploadId);
            if (partNumberMarker != null) {
                queryBuilder.append("&part-number-marker=").append(partNumberMarker);
            }
            String query = queryBuilder.toString();
            String host = extractHost();

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Host", host);
            String amzDate = signer.generateAmzDate();
            headers.put("x-amz-date", amzDate);
            String payloadHash = S3Signer.emptyPayloadHash();
            headers.put("x-amz-content-sha256", payloadHash);

            String auth = signer.sign("GET", uri, query, headers, payloadHash, amzDate);
            headers.put("Authorization", auth);

            String url = this.endpoint + uri + "?" + query;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .headers(okhttp3.Headers.of(flattenHeaders(headers)))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new MinioOperationException("List parts failed, status: "
                            + response.code() + " " + body, new RuntimeException(body));
                }
                String xml = response.body() != null ? response.body().string() : "";
                ListPartsResult result = parseListPartsResult(xml);
                allParts.addAll(result.parts);

                if (result.isTruncated) {
                    partNumberMarker = result.nextPartNumberMarker;
                } else {
                    break;
                }
            } catch (MinioOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new MinioOperationException("List parts failed", e);
            }
        }

        return allParts;
    }

    /**
     * 完成分片上传。
     */
    public ObjectWriteResponse completeMultipartUpload(String bucket, String object,
                                                       String uploadId, Part[] parts) {
        String uri = uri(bucket, object);
        String query = "uploadId=" + uploadId;
        String host = extractHost();

        String xmlBody = buildCompleteMultipartUploadXml(parts);
        byte[] bodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        headers.put("Content-Type", "application/xml");
        String amzDate = signer.generateAmzDate();
        headers.put("x-amz-date", amzDate);
        String payloadHash = S3Signer.sha256Hex(bodyBytes);
        headers.put("x-amz-content-sha256", payloadHash);

        String auth = signer.sign("POST", uri, query, headers, payloadHash, amzDate);
        headers.put("Authorization", auth);

        String url = this.endpoint + uri + "?" + query;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyBytes, XML_MEDIA_TYPE))
                .headers(okhttp3.Headers.of(flattenHeaders(headers)))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new MinioOperationException("Complete multipart upload failed, status: "
                        + response.code() + " " + body, new RuntimeException(body));
            }
            String etag = response.header("ETag");
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }
            okhttp3.Headers respHeaders = response.headers();
            return new ObjectWriteResponse(respHeaders, bucket, region, object, etag, null);
        } catch (MinioOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new MinioOperationException("Complete multipart upload failed", e);
        }
    }

    /**
     * 取消分片上传。
     */
    public void abortMultipartUpload(String bucket, String object, String uploadId) {
        String uri = uri(bucket, object);
        String query = "uploadId=" + uploadId;
        String host = extractHost();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        String amzDate = signer.generateAmzDate();
        headers.put("x-amz-date", amzDate);
        String payloadHash = S3Signer.emptyPayloadHash();
        headers.put("x-amz-content-sha256", payloadHash);

        String auth = signer.sign("DELETE", uri, query, headers, payloadHash, amzDate);
        headers.put("Authorization", auth);

        String url = this.endpoint + uri + "?" + query;
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .headers(okhttp3.Headers.of(flattenHeaders(headers)))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new MinioOperationException("Abort multipart upload failed, status: "
                        + response.code() + " " + body, new RuntimeException(body));
            }
        } catch (MinioOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new MinioOperationException("Abort multipart upload failed", e);
        }
    }

    // ===================== Internal Helpers =====================

    private String uri(String bucket, String object) {
        return "/" + bucket + "/" + encodeObject(object);
    }

    private String encodeObject(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return name;
        }
    }

    private String extractHost() {
        try {
            URL url = new URL(endpoint);
            return url.getHost();
        } catch (Exception e) {
            throw new MinioOperationException("Invalid endpoint: " + endpoint, e);
        }
    }

    private String[] flattenHeaders(Map<String, String> headers) {
        String[] flat = new String[headers.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            flat[i++] = entry.getKey();
            flat[i++] = entry.getValue();
        }
        return flat;
    }

    private String parseUploadId(String xml) throws Exception {
        Document doc = parseXml(xml);
        NodeList nodes = doc.getElementsByTagName("UploadId");
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        throw new MinioOperationException("Cannot find UploadId in response",
                new RuntimeException("Missing UploadId in XML: " + xml));
    }

    private ListPartsResult parseListPartsResult(String xml) throws Exception {
        Document doc = parseXml(xml);
        ListPartsResult result = new ListPartsResult();

        NodeList partNodes = doc.getElementsByTagName("Part");
        for (int i = 0; i < partNodes.getLength(); i++) {
            Element partEl = (Element) partNodes.item(i);
            int partNumber = Integer.parseInt(
                    partEl.getElementsByTagName("PartNumber").item(0).getTextContent());
            String etag = partEl.getElementsByTagName("ETag").item(0).getTextContent();
            if (etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }
            result.parts.add(new Part(partNumber, etag));
        }

        NodeList isTruncatedNodes = doc.getElementsByTagName("IsTruncated");
        if (isTruncatedNodes.getLength() > 0) {
            result.isTruncated = "true".equals(isTruncatedNodes.item(0).getTextContent());
        }

        NodeList markerNodes = doc.getElementsByTagName("NextPartNumberMarker");
        if (markerNodes.getLength() > 0) {
            String val = markerNodes.item(0).getTextContent();
            if (val != null && !val.isEmpty()) {
                result.nextPartNumberMarker = Integer.parseInt(val);
            }
        }

        return result;
    }

    private String buildCompleteMultipartUploadXml(Part[] parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<CompleteMultipartUpload xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        for (Part part : parts) {
            sb.append("<Part>")
                    .append("<PartNumber>").append(part.partNumber()).append("</PartNumber>")
                    .append("<ETag>\"").append(part.etag()).append("\"</ETag>")
                    .append("</Part>");
        }
        sb.append("</CompleteMultipartUpload>");
        return sb.toString();
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static class ListPartsResult {
        List<Part> parts = new ArrayList<>();
        boolean isTruncated;
        Integer nextPartNumberMarker;
    }

}
