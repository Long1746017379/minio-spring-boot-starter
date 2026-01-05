package com.dcl.boot.minio.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author along
 * @date 2025/12/22 22:36
 */
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    /**
     * 是否启用 MinIO
     */
    private boolean enabled = true;

    /**
     * MinIO 服务地址
     */
    private String endpoint = "http://192.168.253.100:19000";

    /**
     * 访问 key
     */
    private String accessKey = "minioadmin";

    /**
     * 密钥
     */
    private String secretKey = "minioadmin";

    /**
     * 默认桶
     */
    private String bucket = "default";


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
