package com.along.boot.minio.callback;

import io.minio.MinioClient;

/**
 * MinIO 操作回调接口，参考 RedisTemplate 的 SessionCallback 设计。
 * <p>当 MinioTemplate 封装的 API 无法满足需求时，可通过此回调直接操作原生 {@link MinioClient}。</p>
 *
 * @param <T> 返回值类型
 * @author along
 */
@FunctionalInterface
public interface MinioCallback<T> {

    /**
     * 在 MinioClient 上下文中执行自定义操作。
     *
     * @param client 原生 MinioClient 实例
     * @return 执行结果
     * @throws Exception 操作中抛出的任意异常
     */
    T doInMinio(MinioClient client) throws Exception;

}
