package com.washy.dify.common.entity.function;

import lombok.Data;

import java.util.List;

/**
 * 函数元数据（全局通用）
 */
@Data
public class FunctionInfo {
    // 函数唯一名称
    private String name;

    // 函数描述（给大模型看）
    private String desc;

    // 参数名列表
    private List<String> params;
}