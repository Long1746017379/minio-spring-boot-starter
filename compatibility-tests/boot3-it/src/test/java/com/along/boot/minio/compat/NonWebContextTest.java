package com.along.boot.minio.compat;

import com.along.boot.minio.autoconfigure.MinioAutoConfiguration;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NonWebContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MinioAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(
                    "org.springframework.web.multipart.MultipartFile"))
            .withBean(MinioClient.class, () -> Mockito.mock(MinioClient.class));

    @Test
    void skipsMinioUtilWhenMultipartFileIsMissing() {
        runner.run(context -> {
            assertThat(context).hasBean("minioTemplate");
            assertThat(context).doesNotHaveBean("minioUtil");
        });
    }
}
