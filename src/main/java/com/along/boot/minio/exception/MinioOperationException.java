package com.along.boot.minio.exception;

/**
 * MinioOperationException
 *
 * @author along
 * @date 2025/12/27 11:32
 */
public class MinioOperationException extends RuntimeException {

    public MinioOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
