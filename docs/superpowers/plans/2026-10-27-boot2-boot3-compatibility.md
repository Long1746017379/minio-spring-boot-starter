# Spring Boot 2.7 ~ 3.x 双版本兼容 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `minio-spring-boot-starter` 以单个 1.1.0 制品，在 Spring Boot 2.7.x ~ 3.x 下零配置自动装配。

**Architecture:** 同时提供 `spring.factories` 与 `AutoConfiguration.imports` 两个注册文件；配置类保持 `@Configuration`；用两个独立 Maven 测试工程分别验证 Boot 2.7.18 与 3.4.1。2.2 ~ 2.6 不承诺开箱即用，在 `使用说明.md` 中给出依赖覆盖方案。

**Tech Stack:** Java 8 字节码、Maven、Spring Boot 2.7.18 / 3.4.1、MinIO SDK 8.5.7、JUnit 5、AssertJ、Mockito、Spring Boot Test。

## Global Constraints

- 产物只有一个：`com.along.boot:minio-spring-boot-starter:1.1.0`，不拆模块、不做 shade。
- 编译目标为 Java 8（`<release>8</release>`），测试工程使用 JDK 17。
- 不修改任何公开包名、类名、方法签名、`minio.*` 配置项。
- 自动配置类继续使用 `@Configuration`，禁止使用 `@AutoConfiguration`。
- 两个注册文件必须同时存在：
  - `src/main/resources/META-INF/spring.factories`
  - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `compatibility-tests/` 下两个工程不加入根 POM 的 `<modules>`。
- 每次修改 starter 后，先执行 `mvn clean install -DskipTests`，再运行测试工程。
- 提交步骤需用户明确同意后执行；若未授权，跳过 `git commit`，保留工作区改动。
- 不删除任何目录；不删除文件，除非用户明确同意。

---

### Task 1: 测试工程骨架 + 复现 Boot 3 自动配置失效

**Files:**
- Modify: `pom.xml`（版本 `1.0.0` → `1.1.0`）
- Create: `compatibility-tests/boot2-it/pom.xml`
- Create: `compatibility-tests/boot3-it/pom.xml`
- Create（两个工程的相对路径相同）:
  - `src/test/java/com/along/boot/minio/compat/CompatibilityTestApplication.java`
  - `src/test/java/com/along/boot/minio/compat/MockMinioClientConfig.java`
  - `src/test/java/com/along/boot/minio/compat/AutoConfigurationTest.java`
  - `src/test/java/com/along/boot/minio/compat/DisabledAutoConfigurationTest.java`

**Interfaces:**
- Consumes: 已安装的 `com.along.boot:minio-spring-boot-starter:1.1.0`
- Produces: 可复用的兼容性测试骨架；`MockMinioClientConfig` 提供一个 Mockito `MinioClient` Bean

- [ ] **Step 1: 修改 starter 版本号**

在 `pom.xml` 中把：

```xml
    <version>1.0.0</version>
```

改为：

```xml
    <version>1.1.0</version>
```

- [ ] **Step 2: 创建 Boot 2.7 测试工程 POM**

`compatibility-tests/boot2-it/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.along.boot</groupId>
    <artifactId>minio-compat-boot2-it</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.along.boot</groupId>
            <artifactId>minio-spring-boot-starter</artifactId>
            <version>1.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 创建 Boot 3.4 测试工程 POM**

`compatibility-tests/boot3-it/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.along.boot</groupId>
    <artifactId>minio-compat-boot3-it</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.along.boot</groupId>
            <artifactId>minio-spring-boot-starter</artifactId>
            <version>1.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建测试应用类（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/CompatibilityTestApplication.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/CompatibilityTestApplication.java`

```java
package com.along.boot.minio.compat;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CompatibilityTestApplication {
}
```

- [ ] **Step 5: 创建 MinioClient Mock 配置（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/MockMinioClientConfig.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/MockMinioClientConfig.java`

```java
package com.along.boot.minio.compat;

import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MockMinioClientConfig {

    @Bean
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }
}
```

- [ ] **Step 6: 创建自动配置测试（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/AutoConfigurationTest.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/AutoConfigurationTest.java`

```java
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
```

- [ ] **Step 7: 创建禁用开关测试（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/DisabledAutoConfigurationTest.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/DisabledAutoConfigurationTest.java`

```java
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
```

- [ ] **Step 8: 安装 starter**

Run:

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS，本地仓库出现 `minio-spring-boot-starter-1.1.0.jar`。

- [ ] **Step 9: 运行 Boot 2.7 测试（应通过）**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
```

Expected: PASS。Boot 2.7 仍读取 `spring.factories`，所以现有实现已能装配。

- [ ] **Step 10: 运行 Boot 3.4 测试（应失败）**

Run:

```bash
mvn -f compatibility-tests/boot3-it test
```

Expected: FAIL。`AutoConfigurationTest.registersBeansThroughAutoConfiguration` 报类似：

```text
expected size: 1 but was: 0
```

这是本次改造要修复的核心问题。

- [ ] **Step 11: 提交（需用户同意）**

```bash
git add pom.xml compatibility-tests
git commit -m "test: add boot2/boot3 compatibility test harness"
```

---

### Task 2: 添加 AutoConfiguration.imports 注册

**Files:**
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Consumes: `com.along.boot.minio.autoconfigure.MinioAutoConfiguration`
- Produces: Spring Boot 3.x 可发现的自动配置注册

- [ ] **Step 1: 创建注册文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```text
com.along.boot.minio.autoconfigure.MinioAutoConfiguration
```

- [ ] **Step 2: 重新安装 starter**

Run:

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行 Boot 3.4 测试（应通过）**

Run:

```bash
mvn -f compatibility-tests/boot3-it test
```

Expected: PASS。

- [ ] **Step 4: 运行 Boot 2.7 测试（应通过）**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
```

Expected: PASS。Boot 2.7 同时读取两个文件并去重，不应出现重复 Bean。

- [ ] **Step 5: 提交（需用户同意）**

```bash
git add src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git commit -m "feat: register auto-configuration for Spring Boot 3"
```

---

### Task 3: POM 清理与 Java 8 release 编译

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: Java 8 字节码、无强制日志实现、无未使用依赖

- [ ] **Step 1: 增加 release 属性**

在 `<properties>` 中追加：

```xml
        <maven.compiler.release>8</maven.compiler.release>
```

- [ ] **Step 2: 替换编译器插件配置**

把：

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.1</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
```

改为：

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
```

- [ ] **Step 3: 删除 logback-classic 依赖**

删除：

```xml
        <!-- Logback 日志实现 -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.11</version>
        </dependency>
```

- [ ] **Step 4: 删除 commons-lang3 依赖**

删除：

```xml
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>
```

- [ ] **Step 5: 重新构建**

Run:

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: 验证字节码版本**

Run:

```bash
javap -verbose "target/classes/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.class" | grep "major version"
```

Expected:

```text
major version: 52
```

- [ ] **Step 7: 回归两个测试工程**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
mvn -f compatibility-tests/boot3-it test
```

Expected: 两个命令都 PASS。

- [ ] **Step 8: 提交（需用户同意）**

```bash
git add pom.xml
git commit -m "build: compile for Java 8 and remove unused logging dependency"
```

---

### Task 4: 新增 create-bucket-on-startup 开关

**Files:**
- Modify: `src/main/java/com/along/boot/minio/properties/MinioProperties.java`
- Modify: `src/main/java/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.java`
- Create（两个测试工程各一份）:
  - `src/test/java/com/along/boot/minio/compat/BucketCreationTest.java`
  - `src/test/java/com/along/boot/minio/compat/NoBucketCreationTest.java`

**Interfaces:**
- Consumes: `MinioProperties`
- Produces: `boolean isCreateBucketOnStartup()`；配置键 `minio.create-bucket-on-startup`

- [ ] **Step 1: 先写默认行为测试（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/BucketCreationTest.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/BucketCreationTest.java`

```java
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
    void checksBucketOnStartupByDefault() {
        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
    }
}
```

- [ ] **Step 2: 先写开关关闭测试（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/NoBucketCreationTest.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/NoBucketCreationTest.java`

```java
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
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
```

Expected: `BucketCreationTest` PASS；`NoBucketCreationTest` FAIL，提示 `minioClient.bucketExists(...)` 被调用，例如：

```text
No interactions wanted here:
-> at com.along.boot.minio.util.MinioBucketManager.createIfAbsent(...)
```

- [ ] **Step 4: 新增配置属性**

在 `MinioProperties.java` 的 `bucket` 字段之后添加：

```java
    /** 是否在启动时检查并创建默认 Bucket */
    private boolean createBucketOnStartup = true;
```

- [ ] **Step 5: 修改自动配置中的建桶逻辑**

在 `MinioAutoConfiguration.java` 的 `minioTemplate` Bean 中，把：

```java
        bucketManager.createIfAbsent(props.getBucket());
        return new DefaultMinioTemplate(minioClient, props, multipartClient);
```

改为：

```java
        if (props.isCreateBucketOnStartup()) {
            bucketManager.createIfAbsent(props.getBucket());
        }
        return new DefaultMinioTemplate(minioClient, props, multipartClient);
```

- [ ] **Step 6: 重新安装并运行测试**

Run:

```bash
mvn clean install -DskipTests
mvn -f compatibility-tests/boot2-it test
mvn -f compatibility-tests/boot3-it test
```

Expected: 全部 PASS。

- [ ] **Step 7: 提交（需用户同意）**

```bash
git add src/main/java/com/along/boot/minio/properties/MinioProperties.java \
        src/main/java/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.java \
        compatibility-tests
git commit -m "feat: make startup bucket creation configurable"
```

---

### Task 5: MinioUtil 按 MultipartFile 条件注册

**Files:**
- Modify: `src/main/java/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.java`
- Create（两个测试工程各一份）:
  - `src/test/java/com/along/boot/minio/compat/NonWebContextTest.java`

**Interfaces:**
- Consumes: `MinioAutoConfiguration`
- Produces: 无 `spring-web` 时不注册 `minioUtil` Bean；其余 Bean 不受影响

- [ ] **Step 1: 先写非 Web 环境测试（两个工程各一份）**

`compatibility-tests/boot2-it/src/test/java/com/along/boot/minio/compat/NonWebContextTest.java`
`compatibility-tests/boot3-it/src/test/java/com/along/boot/minio/compat/NonWebContextTest.java`

```java
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
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
```

Expected: `NonWebContextTest` FAIL。当前实现会无条件注册 `minioUtil`，断言失败，提示：

```text
Expecting actual not to have bean named:
  minioUtil
```

如果出现 `NoClassDefFoundError: org/springframework/web/multipart/MultipartFile`，同样属于预期的失败。

- [ ] **Step 3: 移除外层的 minioUtil Bean**

在 `MinioAutoConfiguration.java` 中删除：

```java
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
```

- [ ] **Step 4: 增加嵌套条件配置类**

在 `MinioAutoConfiguration` 的最后一个 `}` 之前添加：

```java
    /**
     * MultipartFile 相关能力，仅在存在 spring-web 时注册。
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.web.multipart.MultipartFile")
    static class MinioMultipartConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public MinioUtil minioUtil(MinioTemplate minioTemplate) {
            return new MinioUtil(minioTemplate);
        }
    }
```

- [ ] **Step 5: 重新安装并运行测试**

Run:

```bash
mvn clean install -DskipTests
mvn -f compatibility-tests/boot2-it test
mvn -f compatibility-tests/boot3-it test
```

Expected: 全部 PASS。

- [ ] **Step 6: 提交（需用户同意）**

```bash
git add src/main/java/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.java \
        compatibility-tests
git commit -m "fix: register MinioUtil only when spring-web is present"
```

---

### Task 6: 文档更新

**Files:**
- Modify: `README.md`
- Verify: `使用说明.md`（如需补充，直接修改）

**Interfaces:**
- Produces: 与实现一致的版本支持说明

- [ ] **Step 1: 在 README 增加版本兼容章节**

在 `README.md` 的“业务层使用”之前插入：

```markdown
## 版本兼容

| Spring Boot | 是否零配置可用 | 说明 |
|---|---|---|
| 3.x | 是 | 使用 1.1.0+ |
| 2.7.x | 是 | 使用 1.1.0+ |
| 2.2 ~ 2.6 | 需覆盖依赖 | 需覆盖 OkHttp 4.12.0、Kotlin 1.8.21、Jackson 2.15.3 |

完整的引入方式、依赖覆盖片段和常见问题见 [使用说明.md](./使用说明.md)。
```

- [ ] **Step 2: 核对 使用说明.md**

确认以下内容与实现一致：

- starter 版本为 `1.1.0`
- 配置键为 `minio.create-bucket-on-startup`
- 2.2 的 Jackson 属性为 `jackson.version`，2.3 ~ 2.6 为 `jackson-bom.version`
- 2.7 的 OkHttp 属性为 `okhttp.version`，2.2 ~ 2.6 为 `okhttp3.version`

如有不一致，直接修正。

- [ ] **Step 3: 提交（需用户同意）**

```bash
git add README.md 使用说明.md
git commit -m "docs: document Spring Boot 2.7-3.x compatibility"
```

---

### Task 7: 最终验证

**Files:**
- 无新增，只做验证

**Interfaces:**
- Consumes: 前 6 个任务的产物
- Produces: 可交付的 1.1.0 jar 与测试通过记录

- [ ] **Step 1: 干净构建**

Run:

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行 Boot 2.7 兼容性测试**

Run:

```bash
mvn -f compatibility-tests/boot2-it test
```

Expected: BUILD SUCCESS，所有测试 PASS。

- [ ] **Step 3: 运行 Boot 3.4 兼容性测试**

Run:

```bash
mvn -f compatibility-tests/boot3-it test
```

Expected: BUILD SUCCESS，所有测试 PASS。

- [ ] **Step 4: 检查注册文件已打入 jar**

Run:

```bash
jar tf target/minio-spring-boot-starter-1.1.0.jar | grep -E "spring.factories|AutoConfiguration.imports"
```

Expected 输出包含：

```text
META-INF/spring.factories
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- [ ] **Step 5: 检查字节码版本**

Run:

```bash
javap -verbose "target/classes/com/along/boot/minio/autoconfigure/MinioAutoConfiguration.class" | grep "major version"
```

Expected:

```text
major version: 52
```

- [ ] **Step 6: 查看最终改动**

Run:

```bash
git status
git diff --stat
```

Expected: 只包含本计划涉及的文件；没有 `target/` 或临时文件。

- [ ] **Step 7: 提交（需用户同意）**

```bash
git add -A
git commit -m "chore: verify Spring Boot 2.7-3.x compatibility"
```

若前 6 个任务已按任务提交，本步骤可跳过，避免空提交。

---

## 附：计划自检

- Spec 覆盖：
  - 双注册文件 → Task 2
  - Java 8 / 编译器插件 → Task 3
  - 删除 logback-classic、commons-lang3 → Task 3
  - `create-bucket-on-startup` → Task 4
  - `MinioUtil` 条件装配 → Task 5
  - 测试矩阵（2.7.18 / 3.4.1、enabled=false、非 Web） → Task 1 / 4 / 5
  - README 与使用说明 → Task 6
  - 验收标准 → Task 7
- 无 TBD / TODO / 占位符。
- 类型与命名一致：`isCreateBucketOnStartup()`、`minio.create-bucket-on-startup`、Bean 名 `minioTemplate` / `minioUtil`。
