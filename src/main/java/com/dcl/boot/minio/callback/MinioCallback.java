package com.dcl.boot.minio.callback;

import io.minio.MinioClient;

/**
 * @author along
 * @date 2025/12/27 21:03
 */
@FunctionalInterface
public interface MinioCallback<T> {

    T doInMinio(MinioClient client) throws Exception;

}
