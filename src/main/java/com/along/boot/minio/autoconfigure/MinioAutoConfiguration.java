package com.along.boot.minio.autoconfigure;

import com.along.boot.minio.properties.MinioProperties;
import com.along.boot.minio.template.DefaultMinioTemplate;
import com.along.boot.minio.template.MinioTemplate;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author along
 * @date 2025/12/22 22:42
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

//    @Bean
//    @ConditionalOnMissingBean
//    public MinioClient minioClient(MinioProperties props) {
//        // 创建自定义 OkHttpClient 配置超时
//        OkHttpClient httpClient = new OkHttpClient.Builder()
//                .connectTimeout(props.getConnectTimeout(), TimeUnit.MILLISECONDS)
//                .readTimeout(props.getReadTimeout(), TimeUnit.MILLISECONDS)
//                .writeTimeout(props.getWriteTimeout(), TimeUnit.MILLISECONDS)
//                .callTimeout(props.getCallTimeout(), TimeUnit.MILLISECONDS)
//                .build();
//
//        return MinioClient.builder()
//                .endpoint(props.getEndpoint())
//                .credentials(props.getAccessKey(), props.getSecretKey())
//                .httpClient(httpClient)
//                .build();
//    }

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(MinioProperties props) {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        logger.info("MinIO client created successfully, endpoint: {}, bucket: {}",
                props.getEndpoint(), props.getBucket());
        return minioClient;
    }

    @Bean
    @ConditionalOnMissingBean
    public MinioTemplate minioTemplate(MinioClient minioClient, MinioProperties props) {
        return new DefaultMinioTemplate(minioClient, props);
    }

}
