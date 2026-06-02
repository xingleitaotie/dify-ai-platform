package com.washy.dify.llm.exception;

import com.washy.dify.common.exception.GlobalExceptionHandler;

public class LlmException extends GlobalExceptionHandler {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}