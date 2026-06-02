package com.washy.dify.workflow.service;

import com.washy.dify.workflow.entity.NodeOutputType;
import com.washy.dify.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class NodeOutputTypeService {
    
    /**
     * 获取节点的输出类型定义
     */
    public NodeOutputType getNodeOutputType(WorkflowNode node) {
        NodeOutputType type = new NodeOutputType();
        type.setNodeId(node.getNodeId());
        type.setNodeName(node.getName());
        type.setNodeType(node.getNodeType());
        
        // 根据节点类型设置输出变量名和字段
        String outputVar = getOutputVarName(node);
        type.setOutputVar(outputVar);
        
        switch (node.getNodeType()) {
            case "LLM":
                type.setDescription("大语言模型节点输出");
                type.setFields(getLLMOutputFields());
                break;
            case "RAG":
                type.setDescription("知识库检索节点输出");
                type.setFields(getRAGOutputFields());
                break;
            case "START":
                type.setDescription("开始节点输出（输入参数）");
                type.setFields(getStartOutputFields(node));
                break;
            case "FUNCTION":
                type.setDescription("函数节点输出");
                type.setFields(getFunctionOutputFields());
                break;
            case "AGENT":
                type.setDescription("Agent节点输出");
                type.setFields(getAgentOutputFields());
                break;
            case "CODE":
                type.setDescription("代码节点输出");
                type.setFields(getCodeOutputFields());
                break;
            default:
                type.setDescription("通用节点输出");
                type.setFields(getDefaultOutputFields());
        }
        
        return type;
    }
    
    /**
     * 获取节点的输出变量名
     */
    private String getOutputVarName(WorkflowNode node) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> config = mapper.readValue(node.getConfig(), Map.class);
            return (String) config.getOrDefault("outputVar", node.getNodeId() + "_output");
        } catch (Exception e) {
            return node.getNodeId() + "_output";
        }
    }
    
    /**
     * LLM节点输出字段
     */
    private List<NodeOutputType.OutputField> getLLMOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField contentField = new NodeOutputType.OutputField();
        contentField.setName("content");
        contentField.setType("string");
        contentField.setDescription("LLM生成的文本内容");
        fields.add(contentField);
        
        NodeOutputType.OutputField modelField = new NodeOutputType.OutputField();
        modelField.setName("modelConfigId");
        modelField.setType("number");
        modelField.setDescription("使用的模型配置ID");
        fields.add(modelField);
        
        NodeOutputType.OutputField tempField = new NodeOutputType.OutputField();
        tempField.setName("temperature");
        tempField.setType("number");
        tempField.setDescription("使用的温度参数");
        fields.add(tempField);
        
        return fields;
    }
    
    /**
     * RAG节点输出字段
     */
    private List<NodeOutputType.OutputField> getRAGOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField successField = new NodeOutputType.OutputField();
        successField.setName("success");
        successField.setType("boolean");
        successField.setDescription("检索是否成功");
        fields.add(successField);
        
        NodeOutputType.OutputField dataField = new NodeOutputType.OutputField();
        dataField.setName("data");
        dataField.setType("string");
        dataField.setDescription("检索到的知识库内容");
        fields.add(dataField);
        
        NodeOutputType.OutputField queryField = new NodeOutputType.OutputField();
        queryField.setName("queryString");
        queryField.setType("string");
        queryField.setDescription("实际查询的字符串");
        fields.add(queryField);
        
        NodeOutputType.OutputField kbField = new NodeOutputType.OutputField();
        kbField.setName("kbName");
        kbField.setType("string");
        kbField.setDescription("检索的知识库名称");
        fields.add(kbField);
        
        return fields;
    }
    
    /**
     * 开始节点输出字段（从配置中读取）
     */
    private List<NodeOutputType.OutputField> getStartOutputFields(WorkflowNode node) {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> config = mapper.readValue(node.getConfig(), Map.class);
            List<Map<String, String>> inputVariables = (List<Map<String, String>>) config.get("inputVariables");
            
            if (inputVariables != null) {
                for (Map<String, String> varDef : inputVariables) {
                    NodeOutputType.OutputField field = new NodeOutputType.OutputField();
                    field.setName(varDef.get("name"));
                    field.setType("string");  // 可以扩展类型推断
                    field.setDescription(varDef.get("description"));
                    fields.add(field);
                }
            }
        } catch (Exception e) {
            log.warn("解析开始节点配置失败", e);
        }
        
        return fields;
    }
    
    private List<NodeOutputType.OutputField> getFunctionOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField resultField = new NodeOutputType.OutputField();
        resultField.setName("result");
        resultField.setType("any");
        resultField.setDescription("函数执行结果");
        fields.add(resultField);
        
        return fields;
    }
    
    private List<NodeOutputType.OutputField> getAgentOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField resultField = new NodeOutputType.OutputField();
        resultField.setName("result");
        resultField.setType("string");
        resultField.setDescription("Agent执行结果");
        fields.add(resultField);
        
        return fields;
    }
    
    private List<NodeOutputType.OutputField> getCodeOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField resultField = new NodeOutputType.OutputField();
        resultField.setName("result");
        resultField.setType("any");
        resultField.setDescription("代码执行结果");
        fields.add(resultField);
        
        return fields;
    }
    
    private List<NodeOutputType.OutputField> getDefaultOutputFields() {
        List<NodeOutputType.OutputField> fields = new ArrayList<>();
        
        NodeOutputType.OutputField outputField = new NodeOutputType.OutputField();
        outputField.setName("output");
        outputField.setType("any");
        outputField.setDescription("节点输出");
        fields.add(outputField);
        
        return fields;
    }
}