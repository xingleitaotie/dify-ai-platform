package com.washy.dify.prompt.exception;

/**
 * 提示词模块异常
 */
public class PromptException extends RuntimeException {
    
    private String errorCode;
    
    public PromptException(String message) {
        super(message);
    }
    
    public PromptException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PromptException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PromptException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}