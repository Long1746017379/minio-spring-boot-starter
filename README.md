# Minio-Spring-Boot-Starter

## 设计starter

参考RedisTemplate 的设计理念

[GitHub](https://github.com/Long1746017379/minio-spring-boot-starter)

[Gitee](https://gitee.com/chenglong1027/minio-spring-boot-starter)

**代码结构**

```
minio-spring-boot-starter
│
├── pom.xml
│
└── src/main/java
│   └── com/along/boot/minio
│       ├── autoconfigure
│       │   └── MinioAutoConfiguration.java
|		|
│       ├── callback
│       │   └── MinioCallback.java
│       │
│       ├── exception
│       │   └── MinioOperationException.java
│       │
│       ├── properties
│       │   └── MinioProperties.java
│       │
│       ├── template
│       │   └── DefaultMinioTemplate.java
│       │   └── MinioTemplate.java
|		|
│       └── util
│           └── MinioBucketManager.java
│
└── src/main/resources
    └── META-INF
        ├── spring.factories
        └── spring
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports

```



## 引入starter

> pom.xml 中引入starter 依赖

```xml
<dependency>
    <groupId>com.along.boot</groupId>
    <artifactId>minio-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

## 配置文件

```properties
# 如不进行如下配置，则走starter里默认的配置
minio.enabled = true
minio.endpoint = http://192.168.253.100:19000
minio.accessKey = minioadmin
minio.secretKey = minioadmin
minio.bucket = default
minio.create-bucket-on-startup = true
```

## 版本兼容

| Spring Boot | 是否零配置可用 | 说明 |
|---|---|---|
| 3.x | 是 | 使用 1.2.0 |
| 2.7.x | 是 | 使用 1.2.0 |
| 2.2 ~ 2.6 | 需覆盖依赖 | 需覆盖 OkHttp 4.12.0、Kotlin 1.8.21；建议对齐 Jackson 2.15.3 |

完整的引入方式、依赖覆盖片段和常见问题见 [使用说明.md](./使用说明.md)。

## 业务层使用

> 注入 `MinioTemplate` 或 `MinioUtil` 即可使用。非 Web 应用只会注册 `MinioTemplate`；`MinioUtil` 依赖 `spring-web`，缺少时不会创建。

```java
@Autowired
private MinioUtil minioUtil;

// 或
@Autowired
private MinioTemplate minioTemplate;
```

