package com.washy.dify.common.entity.function;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数元数据（全局通用）
 */
@Data
public class FunctionInfo {
    // 函数唯一名称
    private String name;

    // 函数描述（给大模型看）
    private String desc;

    private List<String> params;  // 参数名列表（简化版）
    private String paramsSchema;  // 新增：完整 JSON Schema（给大模型用）

    // 新增：转换为大模型 Function Calling 格式
    public Map<String, Object> toFunctionCallFormat() {
        Map<String, Object> function = new HashMap<>();
        function.put("name", this.name);
        function.put("description", this.desc);

        // 解析 paramsSchema 字符串为 JSON 对象
        if (this.paramsSchema != null && !this.paramsSchema.isEmpty()) {
            try {
                com.alibaba.fastjson2.JSONObject schema =
                        com.alibaba.fastjson2.JSONObject.parseObject(this.paramsSchema);
                function.put("parameters", schema);
            } catch (Exception e) {
                // 如果解析失败，使用默认格式
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("type", "object");
                parameters.put("properties", new HashMap<>());
                parameters.put("required", new ArrayList<>());
                function.put("parameters", parameters);
            }
        } else {
            // 如果没有 Schema，根据 params 列表生成基础 Schema
            Map<String, Object> properties = new HashMap<>();
            List<String> required = new ArrayList<>();
            for (String param : this.params) {
                Map<String, Object> paramDef = new HashMap<>();
                paramDef.put("type", "string");
                paramDef.put("description", "参数：" + param);
                properties.put(param, paramDef);
                required.add(param);
            }
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("type", "object");
            parameters.put("properties", properties);
            parameters.put("required", required);
            function.put("parameters", parameters);
        }

        return function;
    }
}