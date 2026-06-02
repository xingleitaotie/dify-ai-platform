package com.washy.dify.workflow.entity;

import lombok.Data;

import java.util.List;

/**
 * 节点输出类型定义
 */
@Data
public class NodeOutputType {
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String outputVar;
    private String description;
    private String outputType;  // string, json, array
    private List<OutputField> fields;

    @Data
    public static class OutputField {
        private String name;
        private String type;     // string, number, boolean, array, object
        private String description;
    }
}