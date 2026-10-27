# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

- **Build**: `mvn clean package -DskipTests` (project has no tests)
- **Install to local repo**: `mvn clean install -DskipTests`
- **Generate sources JAR**: `mvn source:jar`
- **Java**: Java 8 bytecode via `<release>8</release>`; build with JDK 9+ (verified on 17), Spring Boot 2.2.13.RELEASE, MinIO SDK 8.5.7
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
├── model/
│   ├── ContentCheckResult.java       — Instant-upload check result (NOT_FOUND / EXISTS / STALE)
│   ├── MultipartUploadInfo.java      — In-progress multipart upload info
│   └── MultipartUploadStatus.java    — Uploaded parts and missing-part calculation
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
    ├── CustomMinioClient.java        — Exposes MinioAsyncClient protected multipart APIs
    ├── S3MultipartClient.java        — Thin facade over CustomMinioClient (same public API)
    └── S3Signer.java                 — Legacy AWS Signature V4 implementation (@Deprecated)
```

### Key design decisions

- **Template pattern**: `MinioTemplate` interface + `DefaultMinioTemplate` with `execute(MinioCallback)` escape hatch — same concept as `RedisTemplate.execute(RedisCallback)`.
- **Multipart upload via SDK protected APIs**: MinIO SDK 8.5.x exposes multipart operations only as `protected` methods, so `CustomMinioClient extends MinioAsyncClient` exposes them; `S3MultipartClient` is a thin facade. SDK signing and XML parsing are reused, no hand-written SigV4.
- **Instant upload (秒传)**: Content-based dedup via MD5 hash. On upload, the stream is spooled to a temp file while the hash is computed, then the marker object at `.__minio_hash/{hash}` is checked. If found, does server-side copy instead of re-uploading; a stale marker falls back to a normal upload.
- **Resumable upload**: `resumeUpload()` lists already-uploaded parts via `uploadId`, skips them with `skipFully`, only uploads missing parts, and validates part size/count before merging.
- **Client-side hash check**: `checkContent(bucket, hash)` reports NOT_FOUND / EXISTS / STALE with the source object; `copyFromHash()` performs the server-side copy so the client never transfers a duplicate file.
- **Upload recovery**: `findUploadId()` locates the latest in-progress upload by object name via ListMultipartUploads; `getUploadStatus()` returns uploaded part numbers and sizes so the caller can send only missing parts.
- **Hash-keyed chunked upload**: `initiateMultipartUpload(..., hash)` writes `.__minio_uploads/{hash}` with the uploadId; `findUploadIdByHash()` / `getUploadStatusByHash()` recover progress by MD5; `uploadChunk()` maps 0-based chunkIndex to partNumber+1; `completeUploadByHash()` merges, writes `.__minio_hash/{hash}`, and removes the in-progress marker. Stale in-progress markers are cleaned when `findUploadByHash()` sees `NoSuchUpload`.
- **Auto-configuration gating**: All beans conditional on `@ConditionalOnClass(MinioClient.class)` and `@ConditionalOnProperty(prefix="minio", name="enabled", matchIfMissing=true)`. Each bean also uses `@ConditionalOnMissingBean` to allow user overrides.
- **Timeout properties**: `minio.connectTimeout`/`readTimeout`/`writeTimeout`/`callTimeout` (10s/30s/30s/60s) are defined in `MinioProperties` but currently not applied to `MinioClient`. `S3MultipartClient` has its own hardcoded timeouts (connect=10s, read=30s, write=60s).
- **Logging**: Uses the SLF4J API only (`@Slf4j` / `LoggerFactory`); no logging implementation is pinned by the starter. Logged at INFO level for key lifecycle events (client creation, upload start/complete, failures).

### Configuration properties (`application.properties` / `application.yml`)

| Property | Default | Description |
|---|---|---|
| `minio.enabled` | true | Enable auto-configuration |
| `minio.endpoint` | http://192.168.253.100:19000 | MinIO server URL |
| `minio.accessKey` | minioadmin | Access key |
| `minio.secretKey` | minioadmin | Secret key |
| `minio.bucket` | default | Default bucket name |
| `minio.create-bucket-on-startup` | true | Check/create the default bucket on startup |
| `minio.region` | us-east-1 | AWS region (for multipart) |
| `minio.connectTimeout` | 10000 | Connection timeout (ms) |
| `minio.readTimeout` | 30000 | Read timeout (ms) |
| `minio.writeTimeout` | 30000 | Write timeout (ms) |
| `minio.callTimeout` | 60000 | Call timeout (ms) |
| `minio.secure` | false | Enable TLS |

### Notable implementation details

- `DefaultMinioTemplate.upload()` with unknown size uses 10MB part size: `.stream(stream, -1, 10 * 1024 * 1024)`
- Hash marker objects live under `.__minio_hash/` prefix in the bucket
- Instant upload spools the stream to a temp file for MD5 computation, so memory stays bounded for large files
- Multipart part requests are signed and parsed by the MinIO SDK; no manual SigV4 or XML handling
- `listMultipartUploads` reuses the SDK's URL builder and `Signer.presignV4` but parses the response XML itself: SDK 8.5.x requires a non-empty `<StorageClass>`, while MinIO may return an empty value and break the SDK parser
- The `buildObjectUrl` in `MinioUtil` actually generates a presigned GET URL (not a simple object URL)
- `isObjectExist` in `DefaultMinioTemplate` swallows all exceptions (returns false); `MinioUtil.isObjectExistBak` is the more precise variant that checks for `NoSuchKey` specifically

## Compatibility

- Supports Spring Boot 2.7 ~ 3.x. Both registration files are shipped: `META-INF/spring.factories` (Boot 2.x) and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3.x).
- `minio.create-bucket-on-startup` (default true) controls whether the default bucket is created during startup.
- `MinioUtil` is only registered when `spring-web` (`MultipartFile`) is on the classpath; non-web applications get `MinioTemplate` only.
