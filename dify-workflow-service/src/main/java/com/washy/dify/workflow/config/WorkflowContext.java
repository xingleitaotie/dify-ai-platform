package com.washy.dify.workflow.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
public class WorkflowContext {

    // 用户原始输入（只读，不变）
    private Map<String, Object> inputs = new HashMap<>();

    // 存储节点输出的变量（会被后续节点访问）
    private Map<String, Object> variables = new HashMap<>();

    private String sessionId;

    private String userId;

    // 新增：上一个节点的输出（给下一个节点用）
    private Object lastNodeOutput;

    public Object getLastNodeOutput() {
        return lastNodeOutput;
    }

    public void setLastNodeOutput(Object lastNodeOutput) {
        this.lastNodeOutput = lastNodeOutput;
    }

    /**
     * 设置节点输出变量
     */
    public void setVariable(String key, Object value) {
        if (key != null && !key.isEmpty()) {
            variables.put(key, value);
            log.debug("设置变量: {} = {}", key, value);
        }
    }

    /**
     * 获取变量值
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * 获取所有变量（用于构建节点输入）
     * 返回的是 inputs + variables 的组合
     */
    public Map<String, Object> getAllVariables() {
        Map<String, Object> all = new HashMap<>();
        all.put("inputs", new HashMap<>(inputs));
        all.put("variables", new HashMap<>(variables));
        return all;
    }

    /**
     * 转换为 Map（用于记录日志）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("inputs", inputs);
        map.put("variables", variables);
        map.put("sessionId", sessionId);
        return map;
    }
}