package com.along.boot.minio.model;

/**
 * 秒传内容检查结果。
 * <p>由 {@code MinioTemplate#checkContent(String, String)} 返回，用于在客户端上传前判断内容是否已存在。</p>
 *
 * @author along
 */
public final class ContentCheckResult {

    /**
     * 内容检查状态。
     */
    public enum Status {
        /** 内容哈希不存在 */
        NOT_FOUND,
        /** 内容已存在，且记录的源对象可用 */
        EXISTS,
        /** 哈希标记存在，但记录的源对象已不存在 */
        STALE
    }

    private final Status status;

    private final String sourceObject;

    private final long size;

    private final String etag;

    private ContentCheckResult(Status status, String sourceObject, long size, String etag) {
        this.status = status;
        this.sourceObject = sourceObject;
        this.size = size;
        this.etag = etag;
    }

    /**
     * 构造"未找到"结果。
     *
     * @return 未找到结果
     */
    public static ContentCheckResult notFound() {
        return new ContentCheckResult(Status.NOT_FOUND, null, -1L, null);
    }

    /**
     * 构造"已存在"结果。
     *
     * @param sourceObject 源对象名
     * @param size         源对象大小（字节）
     * @param etag         源对象 ETag
     * @return 已存在结果
     */
    public static ContentCheckResult exists(String sourceObject, long size, String etag) {
        return new ContentCheckResult(Status.EXISTS, sourceObject, size, etag);
    }

    /**
     * 构造"标记失效"结果。
     *
     * @param sourceObject 标记记录的源对象名
     * @return 标记失效结果
     */
    public static ContentCheckResult stale(String sourceObject) {
        return new ContentCheckResult(Status.STALE, sourceObject, -1L, null);
    }

    /**
     * 获取检查状态。
     *
     * @return 检查状态
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 获取源对象名；{@link Status#NOT_FOUND} 时为 null。
     *
     * @return 源对象名
     */
    public String getSourceObject() {
        return sourceObject;
    }

    /**
     * 获取源对象大小；未知时为 -1。
     *
     * @return 源对象大小（字节）
     */
    public long getSize() {
        return size;
    }

    /**
     * 获取源对象 ETag；未知时为 null。
     *
     * @return 源对象 ETag
     */
    public String getEtag() {
        return etag;
    }

    /**
     * 内容是否已存在且源对象可用。
     *
     * @return true 表示可以秒传
     */
    public boolean isExists() {
        return status == Status.EXISTS;
    }

    @Override
    public String toString() {
        return "ContentCheckResult{status=" + status
                + ", sourceObject='" + sourceObject + '\''
                + ", size=" + size
                + ", etag='" + etag + '\''
                + '}';
    }

}
