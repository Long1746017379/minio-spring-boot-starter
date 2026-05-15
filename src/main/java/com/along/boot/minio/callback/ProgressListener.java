package com.along.boot.minio.callback;

/**
 * 上传进度监听器。
 * <p>用于获取文件上传过程中的字节写入进度，适用于大文件上传场景下的进度展示。</p>
 *
 * @author along
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * 进度回调。
     *
     * @param bytesWritten 已写入的字节数
     * @param totalBytes   总字节数（流长度未知时可能与 bytesWritten 相同）
     */
    void onProgress(long bytesWritten, long totalBytes);

}
