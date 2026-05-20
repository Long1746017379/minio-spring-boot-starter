# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

- **Build**: `mvn clean package -DskipTests` (project has no tests)
- **Install to local repo**: `mvn clean install -DskipTests`
- **Generate sources JAR**: `mvn source:jar`
- **Java**: 1.8 source/target, Spring Boot 2.2.13.RELEASE, MinIO SDK 8.5.7
- **IDE hint**: Run `mvn clean compile` after pulling new dependencies

## Architecture

Spring Boot auto-configuration starter wrapping MinIO Java SDK, modelled after `RedisTemplate`. Bean registration is driven by `spring.factories` -> `MinioAutoConfiguration`.

### Module structure

```
src/main/java/com/along/boot/minio/
├── autoconfigure/
│   └── MinioAutoConfiguration.java   — @Configuration, creates all beans
├── callback/
│   ├── MinioCallback.java            — @FunctionalInterface for raw MinioClient operations
│   └── ProgressListener.java         — @FunctionalInterface for upload progress
├── exception/
│   └── MinioOperationException.java  — RuntimeException wrapper for all MinIO errors
├── properties/
│   └── MinioProperties.java          — @ConfigurationProperties(prefix = "minio")
├── template/
│   ├── MinioTemplate.java            — Interface: bucket CRUD, upload/download, presigned URL,
│   │                                    bucket policy, multipart upload, instant upload (秒传)
│   └── DefaultMinioTemplate.java     — Implementation; also contains ProgressInputStream inner class
└── util/
    ├── MinioBucketManager.java       — Ensures default bucket exists on startup
    ├── MinioHashUtil.java            — MD5 hash computation for content dedup
    ├── MinioUtil.java                — Business-layer convenience wrapper (MultipartFile, etc.)
    ├── S3MultipartClient.java        — Raw S3 REST API calls via OkHttp + SigV4
    └── S3Signer.java                 — AWS Signature V4 implementation
```

### Key design decisions

- **Template pattern**: `MinioTemplate` interface + `DefaultMinioTemplate` with `execute(MinioCallback)` escape hatch — same concept as `RedisTemplate.execute(RedisCallback)`.
- **Multipart upload via raw HTTP**: MinIO SDK 8.5.7 doesn't expose multipart operations publicly, so `S3MultipartClient` calls S3 REST API directly using OkHttp + AWS Signature V4 signing.
- **Instant upload (秒传)**: Content-based dedup via MD5 hash. On upload, computes hash, checks for a marker object at `.__minio_hash/{hash}`. If found, does server-side copy instead of re-uploading.
- **Resumable upload**: `resumeUpload()` lists already-uploaded parts via `uploadId`, skips them, only uploads missing parts.
- **Auto-configuration gating**: All beans conditional on `@ConditionalOnClass(MinioClient.class)` and `@ConditionalOnProperty(prefix="minio", name="enabled", matchIfMissing=true)`. Each bean also uses `@ConditionalOnMissingBean` to allow user overrides.
- **Configurable timeouts**: connect=10s, read=30s, write=30s, call=60s in `MinioProperties`. `S3MultipartClient` has its own hardcoded timeouts (connect=10s, read=30s, write=60s).
- **Logging**: Uses Logback (logback-classic 1.2.11). Logged at INFO level for key lifecycle events (client creation, upload start/complete, failures).

### Configuration properties (`application.properties` / `application.yml`)

| Property | Default | Description |
|---|---|---|
| `minio.enabled` | true | Enable auto-configuration |
| `minio.endpoint` | http://192.168.253.100:19000 | MinIO server URL |
| `minio.accessKey` | minioadmin | Access key |
| `minio.secretKey` | minioadmin | Secret key |
| `minio.bucket` | default | Default bucket name |
| `minio.region` | us-east-1 | AWS region (for multipart) |
| `minio.connectTimeout` | 10000 | Connection timeout (ms) |
| `minio.readTimeout` | 30000 | Read timeout (ms) |
| `minio.writeTimeout` | 30000 | Write timeout (ms) |
| `minio.callTimeout` | 60000 | Call timeout (ms) |
| `minio.secure` | false | Enable TLS |

### Notable implementation details

- `DefaultMinioTemplate.upload()` with unknown size uses 10MB part size: `.stream(stream, -1, 10 * 1024 * 1024)`
- Hash marker objects live under `.__minio_hash/` prefix in the bucket
- Instant upload stores entire file in memory (byte array) for MD5 computation — large files may cause OOM
- `S3MultipartClient` parses XML responses manually with `DocumentBuilderFactory` (no JAXB dependency)
- The `buildObjectUrl` in `MinioUtil` actually generates a presigned GET URL (not a simple object URL)
- `isObjectExist` in `DefaultMinioTemplate` swallows all exceptions (returns false); `MinioUtil.isObjectExistBak` is the more precise variant that checks for `NoSuchKey` specifically
