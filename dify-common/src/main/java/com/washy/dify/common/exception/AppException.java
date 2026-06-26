package com.washy.dify.common.exception;

import com.washy.dify.common.result.ResultCode;
import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final Integer code;

    public AppException(String message) {
        super(message);
        this.code = 500;
    }

    public AppException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public AppException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AppException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public AppException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.code = resultCode.getCode();
    }
}