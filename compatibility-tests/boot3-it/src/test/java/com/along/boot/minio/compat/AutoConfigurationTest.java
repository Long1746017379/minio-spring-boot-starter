package com.along.boot.minio.compat;

import com.along.boot.minio.template.MinioTemplate;
import com.along.boot.minio.util.MinioUtil;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MockMinioClientConfig.class)
class AutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersBeansThroughAutoConfiguration() {
        assertThat(context.getBeanNamesForType(MinioTemplate.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(MinioUtil.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(MinioClient.class)).hasSize(1);
    }
}
