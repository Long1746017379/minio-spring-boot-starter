package com.along.boot.minio.compat;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MockMinioClientConfig.class)
class BucketCreationTest {

    @Autowired
    private MinioClient minioClient;

    @Test
    void checksBucketOnStartupByDefault() throws Exception {
        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
    }
}
