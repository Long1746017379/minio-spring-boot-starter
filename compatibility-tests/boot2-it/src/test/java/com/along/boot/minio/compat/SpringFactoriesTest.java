package com.along.boot.minio.compat;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SpringFactoriesTest {

    @Test
    void springFactoriesListsAutoConfiguration() throws Exception {
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring.factories");
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream in = url.openStream()) {
                String content = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                if (content.contains(
                        "com.along.boot.minio.autoconfigure.MinioAutoConfiguration")) {
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).isTrue();
    }
}
