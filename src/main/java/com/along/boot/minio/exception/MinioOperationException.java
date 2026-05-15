package com.along.boot.minio.exception;

import lombok.Getter;

/**
 * MinIO 操作异常。封装 MinIO SDK 调用过程中抛出的受检异常为运行时异常，
 * 简化业务层异常处理。
 *
 * @author along
 */
@Getter
public class MinioOperationException extends RuntimeException {

    /** 错误码，可用于前端或日志归类 */
    private final String errorCode;

    /**
     * 构造异常。
     *
     * @param message 异常描述
     * @param cause   原始异常
     */
    public MinioOperationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * 构造异常（含错误码）。
     *
     * @param message   异常描述
     * @param cause     原始异常
     * @param errorCode 错误码
     */
    public MinioOperationException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
