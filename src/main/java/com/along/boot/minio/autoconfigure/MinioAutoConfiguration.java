package com.along.boot.minio.autoconfigure;

import com.along.boot.minio.properties.MinioProperties;
import com.along.boot.minio.template.DefaultMinioTemplate;
import com.along.boot.minio.template.MinioTemplate;
import com.along.boot.minio.util.MinioBucketManager;
import com.along.boot.minio.util.MinioUtil;
import com.along.boot.minio.util.S3MultipartClient;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 自动配置。
 * <p>当 classpath 中存在 {@link MinioClient} 且配置项 {@code minio.enabled=true}（默认）时生效。</p>
 * <ul>
 *   <li>自动创建 {@link MinioClient} 实例</li>
 *   <li>自动创建 {@link S3MultipartClient} 实例（支持手动分片上传）</li>
 *   <li>自动创建 {@link MinioBucketManager}，按配置兜底创建默认 Bucket</li>
 *   <li>自动注册 {@link MinioTemplate}；存在 spring-web 时注册 {@link MinioUtil}</li>
 * </ul>
 *
 * @author along
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(
        prefix = "minio",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MinioAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MinioAutoConfiguration.class);

    /**
     * 创建 MinIO 客户端。
     *
     * @param props MinIO 配置属性
     * @return MinioClient 实例
     */
    @Bean
    @ConditionalOnMissingBean // 只有当 Spring 容器中不存在指定类型的 Bean 时，才注册当前方法所创建的 Bean
    public MinioClient minioClient(MinioProperties props) {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        logger.info("MinIO client created successfully, endpoint: {}, bucket: {}",
                props.getEndpoint(), props.getBucket());
        return minioClient;
    }

    /**
     * 创建 S3 分片上传客户端，用于手动分片上传（断点续传）。
     *
     * @param props MinIO 配置属性
     * @return S3MultipartClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public S3MultipartClient s3MultipartClient(MinioProperties props) {
        return new S3MultipartClient(
                props.getEndpoint(),
                props.getAccessKey(),
                props.getSecretKey(),
                props.getRegion()
        );
    }

    /**
     * 创建 Bucket 管理器，用于兜底创建默认 Bucket。
     *
     * @param minioClient MinIO 客户端
     * @return MinioBucketManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MinioBucketManager minioBucketManager(MinioClient minioClient) {
        return new MinioBucketManager(minioClient);
    }

    /**
     * 创建 MinIO 操作模板；是否在创建时检查并创建默认 Bucket 由
     * {@code minio.create-bucket-on-startup} 控制。
     *
     * @param minioClient     MinIO 客户端
     * @param props           MinIO 配置属性
     * @param bucketManager   Bucket 管理器
     * @param multipartClient S3 分片上传客户端
     * @return MinioTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MinioTemplate minioTemplate(MinioClient minioClient, MinioProperties props,
                                       MinioBucketManager bucketManager,
                                       S3MultipartClient multipartClient) {
        if (props.isCreateBucketOnStartup()) {
            bucketManager.createIfAbsent(props.getBucket());
        }
        return new DefaultMinioTemplate(minioClient, props, multipartClient);
    }

    /**
     * MultipartFile 相关能力，仅在存在 spring-web 时注册。
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.web.multipart.MultipartFile")
    static class MinioMultipartConfiguration {

        /**
         * 创建 MinIO 工具类。
         *
         * @param minioTemplate MinIO 操作模板
         * @return MinioUtil 实例
         */
        @Bean
        @ConditionalOnMissingBean
        public MinioUtil minioUtil(MinioTemplate minioTemplate) {
            return new MinioUtil(minioTemplate);
        }
    }

}
