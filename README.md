# Minio-Spring-Boot-Starter

## 设计starter

参考RedisTemplate 的设计理念

[GitHub-minio-spring-boot-starter](https://github.com/Long1746017379/minio-spring-boot-starter)

## 引入starter

> pom.xml 中引入starter 依赖

```xml
<dependency>
    <groupId>com.along.boot</groupId>
    <artifactId>minio-spring-boot-starter</artifactId>
    <version>1.0.0</version>
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
```

## 业务层使用

> 业务层使用当前目录下 ./other-import/MinioUtil 即可

