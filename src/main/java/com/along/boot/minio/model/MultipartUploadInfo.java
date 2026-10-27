package com.along.boot.minio.model;

import java.time.ZonedDateTime;

/**
 * 进行中的分片上传信息。
 * <p>由 {@code MinioTemplate#listMultipartUploads(String, String)} 返回。</p>
 *
 * @author along
 */
public final class MultipartUploadInfo {

    private final String uploadId;

    private final String objectName;

    private final ZonedDateTime initiated;

    public MultipartUploadInfo(String uploadId, String objectName, ZonedDateTime initiated) {
        this.uploadId = uploadId;
        this.objectName = objectName;
        this.initiated = initiated;
    }

    /**
     * 获取 uploadId。
     *
     * @return uploadId
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * 获取对象名。
     *
     * @return 对象名
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * 获取上传发起时间。
     *
     * @return 发起时间
     */
    public ZonedDateTime getInitiated() {
        return initiated;
    }

    @Override
    public String toString() {
        return "MultipartUploadInfo{uploadId='" + uploadId + '\''
                + ", objectName='" + objectName + '\''
                + ", initiated=" + initiated
                + '}';
    }

}
