package com.along.boot.minio.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分片上传进度。
 * <p>由 {@code MinioTemplate#getUploadStatus(String, String, String)} 返回，调用方可据此只上传缺失分片。</p>
 *
 * @author along
 */
public final class MultipartUploadStatus {

    private final String bucketName;

    private final String objectName;

    private final String uploadId;

    private final List<Integer> uploadedPartNumbers;

    private final long uploadedBytes;

    public MultipartUploadStatus(String bucketName, String objectName, String uploadId,
                                 List<Integer> uploadedPartNumbers, long uploadedBytes) {
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.uploadId = uploadId;
        List<Integer> sorted = new ArrayList<>(uploadedPartNumbers);
        Collections.sort(sorted);
        this.uploadedPartNumbers = Collections.unmodifiableList(sorted);
        this.uploadedBytes = uploadedBytes;
    }

    /**
     * 获取 Bucket 名称。
     *
     * @return Bucket 名称
     */
    public String getBucketName() {
        return bucketName;
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
     * 获取 uploadId。
     *
     * @return uploadId
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * 获取已上传的分片号（升序）。
     *
     * @return 已上传分片号
     */
    public List<Integer> getUploadedPartNumbers() {
        return uploadedPartNumbers;
    }

    /**
     * 获取已上传的字节总数。
     *
     * @return 已上传字节数
     */
    public long getUploadedBytes() {
        return uploadedBytes;
    }

    /**
     * 根据总分片数计算缺失的分片号。
     *
     * @param partCount 总分片数
     * @return 缺失的分片号（升序）
     */
    public List<Integer> missingPartNumbers(int partCount) {
        Set<Integer> uploaded = new HashSet<>(uploadedPartNumbers);
        List<Integer> missing = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            if (!uploaded.contains(partNumber)) {
                missing.add(partNumber);
            }
        }
        return missing;
    }

    /**
     * 获取已上传的切片编号（0 基）。
     *
     * @return 已上传切片编号
     */
    public List<Integer> getUploadedChunkIndexes() {
        List<Integer> indexes = new ArrayList<>(uploadedPartNumbers.size());
        for (Integer partNumber : uploadedPartNumbers) {
            indexes.add(partNumber - 1);
        }
        return Collections.unmodifiableList(indexes);
    }

    /**
     * 根据总切片数计算缺失的切片编号（0 基）。
     *
     * @param chunkCount 总切片数
     * @return 缺失的切片编号
     */
    public List<Integer> missingChunkIndexes(int chunkCount) {
        List<Integer> indexes = new ArrayList<>();
        for (Integer partNumber : missingPartNumbers(chunkCount)) {
            indexes.add(partNumber - 1);
        }
        return indexes;
    }

    @Override
    public String toString() {
        return "MultipartUploadStatus{uploadId='" + uploadId + '\''
                + ", objectName='" + objectName + '\''
                + ", uploadedPartNumbers=" + uploadedPartNumbers
                + ", uploadedBytes=" + uploadedBytes
                + '}';
    }

}
