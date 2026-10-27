package com.along.boot.minio.compat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SpringFactoriesTest {

    @Test
    void registersAutoConfigurationThroughSpringFactories() throws Exception {
        ClassPathResource resource = new ClassPathResource("META-INF/spring.factories");
        assertThat(resource.exists()).isTrue();

        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        assertThat(content).contains("com.along.boot.minio.autoconfigure.MinioAutoConfiguration");
    }
}
