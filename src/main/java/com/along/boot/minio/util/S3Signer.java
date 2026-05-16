package com.along.boot.minio.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AWS Signature V4 签名工具，用于对 S3 REST API 请求进行签名。
 * <p>参考 AWS Signature V4 文档实现。</p>
 *
 * @author along
 */
public class S3Signer {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String accessKey;
    private final String secretKey;
    private final String region;

    public S3Signer(String accessKey, String secretKey, String region) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
    }

    /**
     * 为 S3 REST API 请求生成 Authorization 头。
     *
     * @param httpMethod  HTTP 方法（GET/POST/PUT/DELETE）
     * @param canonicalUri  规范化 URI（如 /bucket/object）
     * @param canonicalQueryString 规范化查询字符串（如 uploads 或 partNumber=1&uploadId=xxx）
     * @param headers        请求头
     * @param payloadHash    payload 的 SHA-256 十六进制哈希
     * @param amzDate        x-amz-date 值（格式：yyyyMMdd'T'HHmmss'Z'）
     * @return 完整的 Authorization 头值
     */
    public String sign(String httpMethod, String canonicalUri, String canonicalQueryString,
                       java.util.Map<String, String> headers, String payloadHash, String amzDate) {
        String date = amzDate.substring(0, 8);

        String canonicalHeaders = buildCanonicalHeaders(headers);
        String signedHeaders = buildSignedHeaders(headers);

        // 1. Canonical Request
        String canonicalRequest = httpMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        // 2. String to Sign
        String credentialScope = date + "/" + region + "/" + SERVICE + "/aws4_request";
        String canonicalRequestHash = sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + canonicalRequestHash;

        // 3. Signing Key
        byte[] signingKey = getSigningKey(secretKey, date, region, SERVICE);

        // 4. Signature
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // 5. Authorization Header
        return ALGORITHM
                + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    /**
     * 生成 x-amz-date 头的值（UTC 时间）。
     */
    public String generateAmzDate() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(DATE_TIME_FORMATTER);
    }

    /**
     * 计算 payload 的 SHA-256 哈希。
     */
    public static String sha256Hex(byte[] payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(payload));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    /**
     * 计算字符串的 SHA-256 哈希（十六进制）。
     */
    public static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 生成空 payload 的 SHA-256 哈希（用于 GET/DELETE 请求）。
     */
    public static String emptyPayloadHash() {
        return sha256Hex(new byte[0]);
    }

    private String buildCanonicalHeaders(java.util.Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            sorted.put(entry.getKey().toLowerCase(), entry.getValue().trim());
        }
        for (java.util.Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(entry.getKey())
                    .append(":")
                    .append(entry.getValue())
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildSignedHeaders(java.util.Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        java.util.TreeSet<String> sorted = new java.util.TreeSet<>();
        for (String key : headers.keySet()) {
            sorted.add(key.toLowerCase());
        }
        for (String key : sorted) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(key);
        }
        return sb.toString();
    }

    private byte[] getSigningKey(String secretKey, String date, String region, String service) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, date);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    private String hmacSha256Hex(byte[] key, String data) {
        return bytesToHex(hmacSha256(key, data));
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = "0123456789abcdef".charAt(v >>> 4);
            hexChars[i * 2 + 1] = "0123456789abcdef".charAt(v & 0x0F);
        }
        return new String(hexChars);
    }

}
