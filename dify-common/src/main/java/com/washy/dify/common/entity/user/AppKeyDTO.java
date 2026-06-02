package com.washy.dify.common.entity.user;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class AppKeyDTO {
    @NotBlank(message = "应用名称不能为空")
    private String appName;
}