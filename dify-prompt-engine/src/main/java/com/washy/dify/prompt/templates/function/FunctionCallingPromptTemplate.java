package com.washy.dify.prompt.templates.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Function Calling 提示词模板
 */
@Slf4j
@Component
public class FunctionCallingPromptTemplate implements PromptTemplate {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String TEMPLATE = 
        "<|im_start|>system\n" +
        "你是一个函数调用助手。你的任务是将用户请求转换为**严格的 JSON 格式**函数调用。\n\n" +
        "【可用工具】\n" +
        "${tools}\n\n" +
        "【输出格式要求】\n" +
        "如果需要调用函数，输出以下 JSON（不要输出任何其他文字）：\n" +
        "{\"function\": \"函数名\", \"arguments\": {\"参数名\": \"参数值\"}}\n\n" +
        "如果不需要调用函数，直接回答用户问题。\n\n" +
        "【示例1】\n" +
        "用户问：\"现在几点了？\"\n" +
        "输出：{\"function\": \"getCurrentTime\", \"arguments\": {}}\n\n" +
        "【示例2】\n" +
        "用户问：\"1+1等于几？\"\n" +
        "输出：2\n\n" +
        "【示例3】\n" +
        "用户问：\"查询订单号123456的状态\"\n" +
        "输出：{\"function\": \"queryOrder\", \"arguments\": {\"orderId\": \"123456\"}}\n" +
        "<|im_end|>\n" +
        "<|im_start|>user\n" +
        "${question}\n" +
        "<|im_end|>\n" +
        "<|im_start|>assistant\n";
    
    @Override
    public String getName() {
        return "function.calling";
    }
    
    @Override
    public String getVersion() {
        return "v1.3.0";
    }
    
    @Override
    public String getDescription() {
        return "Function Calling 模板，用于将自然语言转换为函数调用";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String render(Map<String, Object> context) {
        String question = (String) context.getOrDefault("question", "");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) context.getOrDefault("tools", new ArrayList<>());
        
        String toolsStr = tools.stream()
            .map(this::formatTool)
            .collect(Collectors.joining("\n"));
        
        return TEMPLATE
            .replace("${tools}", toolsStr)
            .replace("${question}", question);
    }
    
    private String formatTool(Map<String, Object> tool) {
        try {
            return objectMapper.writeValueAsString(tool);
        } catch (JsonProcessingException e) {
            log.warn("格式化工具失败: {}", e.getMessage());
            return tool.toString();
        }
    }
    
    @Override
    public ModelParams getModelParams() {
        return ModelParams.forFunctionCalling();
    }
    
    @Override
    public TemplateType getType() {
        return TemplateType.FUNCTION_CALLING;
    }
}