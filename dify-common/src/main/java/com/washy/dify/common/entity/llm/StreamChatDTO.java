package com.washy.dify.common.entity.llm;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 流式对话请求DTO
 * @author washby
 * @date 2025-12-19
 */
@Data
@ApiModel("流式对话请求参数")
public class StreamChatDTO {

    @ApiModelProperty(value = "会话ID(用于上下文记忆)", required = true)
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @ApiModelProperty(value = "用户提问内容", required = true)
    @NotBlank(message = "提问内容不能为空")
    private String message;
}