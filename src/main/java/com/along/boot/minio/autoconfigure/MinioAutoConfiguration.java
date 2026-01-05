package com.along.boot.minio.autoconfigure;

import com.along.boot.minio.properties.MinioProperties;
import com.along.boot.minio.template.DefaultMinioTemplate;
import com.along.boot.minio.template.MinioTemplate;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MinioTemplate minioTemplate(MinioClient minioClient,
                                       MinioProperties props) {
        return new DefaultMinioTemplate(minioClient, props);
    }

}
