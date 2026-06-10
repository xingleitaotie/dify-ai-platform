package com.washy.dify.provider.exception;

import com.washy.dify.common.exception.GlobalExceptionHandler;

public class ModelProviderException extends GlobalExceptionHandler {

    public ModelProviderException(String message) {
        super(message);
    }

    public ModelProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}