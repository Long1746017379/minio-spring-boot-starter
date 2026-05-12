package com.along.boot.minio.exception;

import lombok.Getter;

/**
 * MinioOperationException
 *
 * @author along
 * @date 2025/12/27 11:32
 */
@Getter
public class MinioOperationException extends RuntimeException {

    private final String errorCode;

    public MinioOperationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public MinioOperationException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
