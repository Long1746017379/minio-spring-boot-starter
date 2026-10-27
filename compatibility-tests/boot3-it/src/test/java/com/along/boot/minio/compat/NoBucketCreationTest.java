package com.along.boot.minio.compat;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "minio.create-bucket-on-startup=false")
@Import(MockMinioClientConfig.class)
class NoBucketCreationTest {

    @Autowired
    private MinioClient minioClient;

    @Test
    void skipsBucketCheckWhenDisabled() {
        verifyNoInteractions(minioClient);
    }
}
