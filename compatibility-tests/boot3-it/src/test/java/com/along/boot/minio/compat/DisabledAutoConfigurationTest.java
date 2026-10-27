package com.along.boot.minio.compat;

import com.along.boot.minio.template.MinioTemplate;
import com.along.boot.minio.util.MinioUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "minio.enabled=false")
class DisabledAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void disabledPropertySkipsAllMinioBeans() {
        assertThat(context.getBeanNamesForType(MinioTemplate.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MinioUtil.class)).isEmpty();
    }
}
