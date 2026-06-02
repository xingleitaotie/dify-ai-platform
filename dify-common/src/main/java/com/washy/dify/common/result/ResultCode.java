package com.washy.dify.common.result;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    FUNCTION_NOT_FOUND(600, "函数不存在"),
    FUNCTION_PARAM_ERROR(601, "函数参数错误"),
    FUNCTION_EXECUTE_ERROR(602, "函数执行失败"),
    FUNCTION_TIMEOUT(603, "函数执行超时"),
    // 👇 新增用户模块错误码
    USER_EXIST(604, "用户名已存在"),
    USER_NOT_EXIST(605, "用户名不存在"),
    //新增 工作流状态码
    WORKFLOW_NOT_FOUND(606, "工作流不存在"),
    WORKFLOW_NOT_NODE(607,"节点不存在");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}