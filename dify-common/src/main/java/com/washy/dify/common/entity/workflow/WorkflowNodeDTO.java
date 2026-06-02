package com.washy.dify.common.entity.workflow;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class WorkflowNodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type;
    private String name;
    private Map<String, Object> config;
    private Position position;

    @Data
    public static class Position implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double x;
        private Double y;
    }
}