package com.along.boot.minio.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 客户端配置属性。
 * <p>在 {@code application.properties} 或 {@code application.yml} 中以 {@code minio.*} 前缀配置。</p>
 *
 * @author along
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** 是否启用 MinIO 自动配置 */
    private boolean enabled = true;

    /** MinIO 服务端点地址 */
    private String endpoint = "http://192.168.253.100:19000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 密钥 */
    private String secretKey = "minioadmin";

    /** 默认存储桶名称 */
    private String bucket = "default";

    /** 是否在启动时检查并创建默认 Bucket */
    private boolean createBucketOnStartup = true;

    /** 是否启用 SSL/TLS */
    private boolean secure = false;

    /** 连接超时时间（毫秒） */
    private int connectTimeout = 10000;

    /** 读取超时时间（毫秒） */
    private int readTimeout = 30000;

    /** 写入超时时间（毫秒） */
    private int writeTimeout = 30000;

    /** 调用超时时间（毫秒） */
    private int callTimeout = 60000;

    /** MinIO 服务区域（用于 multipart upload 等操作） */
    private String region = "us-east-1";

}
