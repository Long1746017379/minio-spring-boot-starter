package com.along.boot.minio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author along
 * @date 2025/12/22 22:36
 */
@Data
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

    /**
     * SSL 支持
     */
    private boolean secure = false;
    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 30000;
    /**
     * 写入超时（毫秒）
     */
    private int writeTimeout = 30000;

    /**
     * 调用超时（毫秒）
     */
    private int callTimeout = 60000;

}
