# Spring Boot 2.7 ~ 3.x 双版本兼容设计

- 项目：`com.along.boot:minio-spring-boot-starter`
- 日期：2026-10-27
- 状态：待评审

## 1. 背景

当前 starter 通过 `META-INF/spring.factories` 注册 `MinioAutoConfiguration`。Spring Boot 3.0 起，自动配置只从 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 读取，`spring.factories` 中的 `EnableAutoConfiguration` 被忽略。

在 Spring Boot 3.4.1 上的实测结果：

- 只引入 starter 依赖：上下文正常启动，但 `MinioTemplate` / `MinioClient` Bean 数量均为 0，自动配置静默失效。
- 补上 `AutoConfiguration.imports`：配置类被加载，Bean 开始创建。

除注册机制外，源码使用的 Spring API、Java 8 字节码、MinIO SDK 都与 Boot 3 兼容，无需 jakarta 迁移（`javax.xml.parsers`、`javax.crypto` 属于 JDK 模块）。

因此目标是：**单制品、消费方零配置，自动适配 Spring Boot 2.7 ~ 3.x**。

## 2. 目标与非目标

### 目标

1. 同一个 jar 在 Spring Boot 2.7.x 与 3.x 下都能自动装配，消费方只加 Maven 依赖。
2. 保持 Java 8 字节码，兼容 2.x（Java 8+）与 3.x（Java 17+）。
3. 不改变现有包名、类名、方法签名与 `minio.*` 配置项。
4. 用自动化测试验证两个版本的装配结果。

### 非目标

1. 不保证 Spring Boot 2.2 ~ 2.6 开箱即用（原因见第 3 节）；只做代码兼容 + 文档说明。
2. 不做运行时版本探测（如 `minio.boot-version=2|3`），脆弱且无法改变依赖解析。
3. 不做双制品（`starter` + `starter-boot3`）或 shade/relocate。
4. 不在本设计中修复分片上传签名、秒传 `<5MiB` 失败等已有功能缺陷，这些另立设计。
5. 不支持 Spring Boot 1.x。

## 3. 支持矩阵

| Boot 版本 | 状态 | 说明 |
|---|---|---|
| 3.x | 正式支持 | 开箱即用 |
| 2.7.x | 正式支持 | 开箱即用 |
| 2.2 ~ 2.6 | 代码兼容，不保证 | 需消费方覆盖 OkHttp / Kotlin / Jackson 版本 |

### 3.1 为什么 2.2 ~ 2.6 不能零配置

Maven 在应用启动前完成依赖解析，消费方的 Spring Boot BOM 优先级高于传递依赖，starter 的 `dependencyManagement` 不会传递给消费方。

实测（消费方导入对应 Boot BOM 后解析出的版本）：

| 消费方 Boot | OkHttp | Jackson | 结论 |
|---|---|---|---|
| 2.2.13.RELEASE | 3.14.9 | 2.10.5 | OkHttp 4.x 的 `RequestBody.create(byte[], MediaType)` 等二进制签名不存在，运行时会 `NoSuchMethodError` |
| 2.7.18 | 4.9.3 | 2.13.5 | 同为 OkHttp 4.x 线，可用 |
| 3.4.1 | 4.12.0 | 2.18.2 | 干净 |

Spring Boot 2.3 ~ 2.6 的 BOM 同样把 `okhttp3.version` 固定为 3.14.9，直到 2.7 才改为导入 `okhttp-bom` 4.9.3。MinIO 8.5.7 自身声明依赖 OkHttp 4.12.0 / Jackson 2.15.3，因此 2.2 ~ 2.6 必须由消费方覆盖。README 提供覆盖片段。

## 4. 设计决策

### 4.1 单制品双注册

产物保持一个：`com.along.boot:minio-spring-boot-starter`。

同时提供两个注册文件：

```
src/main/resources/META-INF/spring.factories
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`spring.factories` 内容保持不变：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.along.boot.minio.autoconfigure.MinioAutoConfiguration
```

新增文件内容：

```
com.along.boot.minio.autoconfigure.MinioAutoConfiguration
```

版本行为：

- Boot 2.0 ~ 2.6：只读 `spring.factories`
- Boot 2.7：两个文件都读，Spring Boot 自动去重
- Boot 3.x：只读 `AutoConfiguration.imports`

`MinioAutoConfiguration` 继续使用 `@Configuration`，不使用 `@AutoConfiguration`。后者从 2.7 才引入，使用它会破坏 2.2 ~ 2.6 的 best-effort 兼容；`@Configuration` 已实测可在 Boot 3.4.1 下被 imports 正常加载。

### 4.2 编译基线

| 项 | 取值 |
|---|---|
| Java 字节码 | 8 |
| 编译器插件 | `maven-compiler-plugin` 3.11.0 |
| 编译参数 | `<release>8</release>` |
| 编译基线 BOM | `spring-boot-dependencies:2.2.13.RELEASE` |

使用 `release` 而不是 `source/target`，避免 JDK 17 编译时链接到新版本 JDK API。

### 4.3 依赖策略

| 依赖 | 处理 |
|---|---|
| `spring-boot-starter` | 保留，版本由消费方 BOM 决定 |
| `spring-boot-autoconfigure` | 保留 |
| `spring-boot-configuration-processor` | 保留，optional |
| `spring-web` | 保留，optional |
| `minio` | 保留 8.5.7 |
| `okhttp` / `okhttp-urlconnection` | 保留 dependencyManagement 中的 4.12.0 直配，仅保证 starter 自身编译 |
| `lombok` | 保留，provided |
| `logback-classic` | 删除。强制日志实现不应由 starter 决定，且 1.2.11 与 Boot 3 的 1.5.x 存在冲突风险 |
| `commons-lang3` | 删除，项目未使用 |

删除 `logback-classic` 后，starter 源码仍通过 slf4j-api 打日志；Spring Boot 应用由 `spring-boot-starter-logging` 提供实现。

### 4.4 条件装配

`MinioUtil` 引用了 optional 的 `org.springframework.web.multipart.MultipartFile`。在非 Web 应用中直接注册该 Bean 有 `NoClassDefFoundError` 风险。

将 `minioUtil` Bean 移入嵌套配置类，并按类名做条件判断（`name` 形式避免注解解析时加载目标类）：

```java
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

`MinioTemplate`、`MinioClient`、`MinioBucketManager`、`S3MultipartClient` 仍无条件注册。

### 4.5 启动建桶开关

新增 `MinioProperties.createBucketOnStartup`，默认 `true`，保持现有行为：

| 属性 | 默认 | 说明 |
|---|---|---|
| `minio.create-bucket-on-startup` | true | false 时启动阶段不检查/创建默认 Bucket |

`minioTemplate` Bean 创建逻辑改为：

```java
if (props.isCreateBucketOnStartup()) {
    bucketManager.createIfAbsent(props.getBucket());
}
```

默认值保持 true，避免语义变化；Kubernetes 等 MinIO 可能晚于应用启动的场景可设置为 false。

## 5. 实现清单

### 5.1 新增文件

1. `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. `compatibility-tests/boot2-it/`（独立 Maven 工程）
3. `compatibility-tests/boot3-it/`（独立 Maven 工程）

### 5.2 修改文件

1. `pom.xml`
   - 升级 `maven-compiler-plugin` 到 3.11.0，改用 `<release>8</release>`
   - 删除 `logback-classic`、`commons-lang3` 依赖
2. `MinioProperties.java`
   - 新增 `createBucketOnStartup` 属性
3. `MinioAutoConfiguration.java`
   - `minioTemplate` 中按开关决定是否建桶
   - `minioUtil` 移入 `MinioMultipartConfiguration` 并加 `@ConditionalOnClass`
4. `README.md`
   - 增加支持矩阵与 Boot 2.2 ~ 2.6 覆盖片段

## 6. 兼容性测试方案

### 6.1 工程结构

两个独立 Maven 工程，不纳入主构建（根 POM 无 `<modules>`）：

| 工程 | 父 POM | JDK | 依赖 |
|---|---|---|---|
| `compatibility-tests/boot2-it` | Spring Boot 2.7.18 | 17 | starter、starter-web、starter-test |
| `compatibility-tests/boot3-it` | Spring Boot 3.4.1 | 17 | starter、starter-web、starter-test |

两个工程都使用 JDK 17：Boot 2.7.18 官方支持 Java 17，可以避免维护两套 JDK。

执行顺序：

```bash
mvn clean install -DskipTests
mvn -f compatibility-tests/boot2-it test
mvn -f compatibility-tests/boot3-it test
```

### 6.2 测试用例

用一个 Mockito mock 的 `MinioClient` Bean 替代真实客户端（依靠 `@ConditionalOnMissingBean` 让 starter 退让），避免依赖真实 MinIO。

| 用例 | 断言 |
|---|---|
| Web 上下文启动 | `MinioTemplate`、`MinioUtil`、`MinioClient` Bean 均存在 |
| `minio.enabled=false` | 三个 Bean 均不存在 |
| `minio.create-bucket-on-startup=false` | 上下文启动成功，且 `MinioClient.bucketExists` 从未被调用 |
| 非 Web 环境（`ApplicationContextRunner` + `FilteredClassLoader` 隐藏 `org.springframework.web.multipart`） | `MinioTemplate` 存在，`MinioUtil` 不存在 |

`FilteredClassLoader` 来自 `spring-boot-test`，可在同一测试工程内模拟 `spring-web` 缺失，无需单独建模块。

### 6.3 测试边界

兼容性测试验证的是**自动装配和条件判断**，不验证真实 MinIO 读写。真实上传/下载的跨版本验证需要 MinIO 实例，作为后续可选增强（Testcontainers）。

## 7. 验收标准

1. `mvn clean install -DskipTests` 成功，产物可被本地仓库解析。
2. `compatibility-tests/boot2-it` 全部用例通过。
3. `compatibility-tests/boot3-it` 全部用例通过。
4. `javap -verbose` 检查 `MinioAutoConfiguration.class` 的 major version 为 52（Java 8）。
5. 在一个真实 Boot 3.x 应用中引入依赖，无需任何额外配置即可注入 `MinioTemplate`。

## 8. 风险与限制

1. **Boot 2.7 的 OkHttp 为 4.9.3**：与 MinIO 声明的 4.12.0 同属 4.x 线，装配测试通过；真实读写建议在引入后做一次冒烟验证。如遇问题，覆盖到 4.12.0。
2. **Boot 2.2 ~ 2.6 需消费方覆盖依赖**：无法由 starter 单方面解决，README 必须写清楚。
3. **删除 logback-classic**：非 Spring Boot 环境下使用 starter 的应用需要自行提供 SLF4J 绑定。
4. **`spring.factories` 与 imports 同时存在**：Boot 2.7 会读取两份并去重；这是官方支持的行为。
5. **启动建桶默认仍为 true**：MinIO 不可达时应用启动失败，属于现有行为，可通过新属性关闭。

## 9. 使用说明（README 变更）

### 9.1 支持矩阵

同第 3 节。

### 9.2 Boot 2.2 ~ 2.6 覆盖片段

使用 `spring-boot-starter-parent` 时可直接覆盖属性（Jackson 属性名：2.2 为 `jackson.version`，2.3 ~ 2.6 为 `jackson-bom.version`）：

```xml
<properties>
    <okhttp3.version>4.12.0</okhttp3.version>
    <kotlin.version>1.8.21</kotlin.version>
    <jackson-bom.version>2.15.3</jackson-bom.version>
</properties>
```

使用 BOM import 时，在自有 `dependencyManagement` 中追加：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>1.8.21</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.15.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
            <version>2.15.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 10. 参考数据

Spring Boot 3.4.1 探针实测：

```text
只加 starter 依赖：
RESULT: CONTEXT_STARTED
RESULT: minioTemplate bean count = 0
RESULT: MinioClient bean count = 0

补 AutoConfiguration.imports 后：
RESULT: CONTEXT_FAILED
Caused by: com.along.boot.minio.exception.MinioOperationException: Create bucket failed: default
Caused by: java.net.ConnectException: Connection refused
```

第二组结果中的失败来自探针故意配置的不可达 endpoint，说明配置类已被加载并开始创建 Bean。

OkHttp 方法签名对比（javap）：

```text
OkHttp 3.14.9:  RequestBody.create(MediaType, byte[]) 等 5 个重载
OkHttp 4.12.0:  额外提供 RequestBody.create(byte[], MediaType) 与 create(byte[])
```

`S3MultipartClient` 使用的是 4.x 才有的参数顺序和单参数重载，因此在 OkHttp 3.14.9 下会在运行时抛出 `NoSuchMethodError`。
