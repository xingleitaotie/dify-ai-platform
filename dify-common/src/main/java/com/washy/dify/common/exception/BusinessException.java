package com.washy.dify.common.exception;

import com.washy.dify.common.result.ResultCode;

public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }

}
