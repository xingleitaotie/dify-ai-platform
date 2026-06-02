package com.washy.dify.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.entity.workflow.WorkflowExecuteDTO;
import com.washy.dify.common.entity.workflow.WorkflowExecuteResultDTO;
import com.washy.dify.common.entity.workflow.WorkflowExecutionDTO;
import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.entity.*;
import com.washy.dify.workflow.mapper.*;
import com.washy.dify.workflow.node.NodeExecutor;
import com.washy.dify.workflow.service.WorkflowExecuteService;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class WorkflowExecuteServiceImpl implements WorkflowExecuteService {

    @Resource
    private WorkflowMapper workflowMapper;
    @Resource
    private WorkflowNodeMapper nodeMapper;
    @Resource
    private WorkflowEdgeMapper edgeMapper;
    @Resource
    private WorkflowExecutionMapper executionMapper;
    @Resource
    private WorkflowNodeExecutionMapper nodeExecutionMapper;
    @Resource
    private ObjectMapper objectMapper;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private List<NodeExecutor> executors;

    private WorkflowVariableResolver resolver;

    private final Map<String, NodeExecutor> executorMap = new ConcurrentHashMap<>();

    public WorkflowExecuteServiceImpl(List<NodeExecutor> executors,
                                      WorkflowVariableResolver resolver) {
        this.executors = executors;
        this.resolver = resolver;
    }

    @PostConstruct
    public void init() {
        for (NodeExecutor executor : executors) {
            executorMap.put(executor.getNodeType(), executor);
            log.info("注册节点执行器: {}", executor.getNodeType());
        }
    }

    private WorkflowNodeExecution executeNode(WorkflowNode node, WorkflowContext context,
                                              String executionId, Map<String, WorkflowNode> nodeMap) {
        WorkflowNodeExecution nodeExec = new WorkflowNodeExecution();
        nodeExec.setExecutionId(executionId);
        nodeExec.setWorkflowId(node.getWorkflowId());
        nodeExec.setNodeId(node.getNodeId());
        nodeExec.setNodeName(node.getName());
        nodeExec.setNodeType(node.getNodeType());
        nodeExec.setStartTime(new Date());
        nodeExec.setStatus("RUNNING");

        long startTime = System.currentTimeMillis();

        try {
            // ===================== 【关键修改】START / END 不执行业务逻辑 =====================
            if ("START".equals(node.getNodeType())) {
                // 开始节点：只把用户输入作为输出，不执行任何逻辑
                nodeExec.setNodeInput(objectMapper.writeValueAsString(context.getInputs()));
                nodeExec.setNodeOutput(objectMapper.writeValueAsString(context.getInputs()));
                nodeExec.setStatus("SUCCESS");
                return nodeExec;
            }
            if ("END".equals(node.getNodeType())) {
                // 结束节点：只接收上一节点结果，不执行，直接作为最终输出
                Map<String, Object> endInput = new HashMap<>();
                endInput.put("lastNodeOutput", context.getLastNodeOutput());
                endInput.put("variables", context.getVariables());

                nodeExec.setNodeInput(objectMapper.writeValueAsString(endInput));
                nodeExec.setNodeOutput(objectMapper.writeValueAsString(context.getLastNodeOutput()));
                nodeExec.setStatus("SUCCESS");
                return nodeExec;
            }
            // ==============================================================================

            Map<String, Object> config = parseConfig(node.getConfig());
            Map<String, Object> nodeInput = buildNodeInput(node, config, context);

            nodeExec.setNodeInput(objectMapper.writeValueAsString(nodeInput));

            NodeExecutor executor = executorMap.get(node.getNodeType());
            if (executor == null) {
                throw new RuntimeException("未找到节点类型对应的执行器: " + node.getNodeType());
            }

            // 执行节点
            Object output = executor.execute(nodeInput, context);
            nodeExec.setNodeOutput(objectMapper.writeValueAsString(output));
            nodeExec.setStatus("SUCCESS");

            // 输出变量存入上下文
            String outputVar = config != null ? (String) config.get("outputVar") : null;
            if (outputVar != null && !outputVar.isEmpty() && output != null) {
                Object outputValue = extractOutputValue(output);
                context.setVariable(outputVar, outputValue);
                log.info("节点 {} 输出变量 {} = {}", node.getName(), outputVar, outputValue);
            }

            // ===================== 【关键】把当前节点输出存入上下文，给下一个节点用 =====================
            context.setLastNodeOutput(output);

        } catch (Exception e) {
            log.error("节点执行失败: {}", node.getName(), e);
            nodeExec.setStatus("FAILED");
            nodeExec.setErrorMsg(e.getMessage());
        }

        nodeExec.setEndTime(new Date());
        nodeExec.setCostTime(System.currentTimeMillis() - startTime);

        return nodeExec;
    }

    /**
     * 构建精简的节点输入
     * 只传递节点真正需要的输入，而不是整个上下文
     */
    private Map<String, Object> buildNodeInput(WorkflowNode node, Map<String, Object> config, WorkflowContext context) {
        Map<String, Object> nodeInput = new HashMap<>();

        // 1. 节点配置
        nodeInput.put("config", getBusinessConfig(node.getNodeType(), config));

        // 2. 运行时输入（只传：用户输入 + 变量 + 上一个节点输出）
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("input", context.getInputs());           // input.query
        runtime.put("vars", context.getVariables());        // var.xxx
        runtime.put("prevOutput", context.getLastNodeOutput()); // 上一节点输出

        nodeInput.put("runtime", runtime);
        return nodeInput;
    }

    /**
     * 根据节点类型提取业务配置字段
     */
    private Map<String, Object> getBusinessConfig(String nodeType, Map<String, Object> fullConfig) {
        if (fullConfig == null) {
            return new HashMap<>();
        }

        Map<String, Object> businessConfig = new HashMap<>();

        // 定义各节点类型的业务字段
        String[] businessFields;
        switch (nodeType) {
            case "LLM":
                businessFields = new String[]{"systemPrompt", "userPrompt", "temperature", "modelConfigId",
                        "outputVar", "outputType", "arrayItemType", "outputFields"};
                break;
            case "RAG":
                businessFields = new String[]{"query", "kbName", "topK", "threshold", "outputVar"};
                break;
            case "FUNCTION":
                businessFields = new String[]{"functionName", "parameters", "outputVar"};
                break;
            case "AGENT":
                businessFields = new String[]{"agentId", "query", "outputVar"};
                break;
            case "CONDITION":
                businessFields = new String[]{"expression", "outputVar"};
                break;
            case "CODE":
                businessFields = new String[]{"code", "language", "outputVar"};
                break;
            case "START":
                businessFields = new String[]{"inputVariables"};
                break;
            case "END":
                businessFields = new String[]{"outputVariables"};
                break;
            default:
                businessFields = new String[]{};
        }

        for (String field : businessFields) {
            if (fullConfig.containsKey(field)) {
                businessConfig.put(field, fullConfig.get(field));
            }
        }

        return businessConfig;
    }

    /**
     * 提取节点的实际输出值
     * 过滤掉内部字段，只保留业务数据
     */
    private Object extractOutputValue(Object output) {
        if (output == null) return null;

        // 1. 数组、字符串直接返回
        if (output instanceof List || output instanceof String) {
            return output;
        }

        // 2. 处理 Map（节点输出结构）
        if (output instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) output;

            // ✅【关键修复】单字段输出（LLM/RAG/函数）自动剥壳，取出真实值
            if (map.size() == 1) {
                return map.values().iterator().next();
            }

            // -------------------
            // 你原来的逻辑保留
            // -------------------
            Map<String, Object> outputMap = (Map<String, Object>) map;
            String[] excludeKeys = {"_raw", "modelConfigId", "temperature", "costTime", "nodeId", "nodeType"};

            if (outputMap.containsKey("content")) return outputMap.get("content");
            if (outputMap.containsKey("answer")) return outputMap.get("answer");
            if (outputMap.containsKey("result")) return outputMap.get("result");

            Map<String, Object> cleaned = new HashMap<>();
            for (Map.Entry<String, Object> entry : outputMap.entrySet()) {
                boolean exclude = false;
                for (String key : excludeKeys) {
                    if (entry.getKey().equals(key)) {
                        exclude = true;
                        break;
                    }
                }
                if (!exclude && entry.getValue() != null) {
                    cleaned.put(entry.getKey(), entry.getValue());
                }
            }
            return cleaned.isEmpty() ? output : cleaned;
        }

        return output;
    }



    @Override
    @Transactional
    public WorkflowExecuteResultDTO executeWorkflow(WorkflowExecuteDTO dto) {
        String executionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            // 获取工作流配置
            Workflow workflow = workflowMapper.selectById(dto.getWorkflowId());
            if (workflow == null) {
                throw new RuntimeException("工作流不存在");
            }

            // 获取节点和边
            List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(dto.getWorkflowId());
            List<WorkflowEdge> edges = edgeMapper.selectByWorkflowId(dto.getWorkflowId());

            Map<String, WorkflowNode> nodeMap = new HashMap<String, WorkflowNode>();
            for (WorkflowNode node : nodes) {
                nodeMap.put(node.getNodeId(), node);
            }

            // 创建执行记录
            WorkflowExecution execution = createExecutionRecord(executionId, workflow, dto);
            executionMapper.insert(execution);

            // 执行上下文
            WorkflowContext context = new WorkflowContext();
            context.setInputs(dto.getInputs());
            context.setVariables(new HashMap<String, Object>());
            context.setSessionId(dto.getSessionId());

            // 找到开始节点
            WorkflowNode startNode = null;
            for (WorkflowNode node : nodes) {
                if ("START".equals(node.getNodeType())) {
                    startNode = node;
                    break;
                }
            }
            if (startNode == null) {
                throw new RuntimeException("未找到开始节点");
            }

            // 执行工作流
            WorkflowNode currentNode = startNode;
            WorkflowNodeExecution currentNodeExecution = null;

            while (currentNode != null) {
                // 执行当前节点
                currentNodeExecution = executeNode(currentNode, context, executionId, nodeMap);
                nodeExecutionMapper.insert(currentNodeExecution);
                try {
                    if (!"START".equals(currentNode.getNodeType()) && !"END".equals(currentNode.getNodeType())) {
                        Object output = objectMapper.readValue(currentNodeExecution.getNodeOutput(), Object.class);
                        context.setLastNodeOutput(output);
                    }
                } catch (Exception e) {
                    log.error("设置上一节点输出失败", e);
                }
                if (!"SUCCESS".equals(currentNodeExecution.getStatus())) {
                    // 节点执行失败
                    execution.setStatus("FAILED");
                    execution.setErrorMsg(currentNodeExecution.getErrorMsg());
                    break;
                }

                // 如果是结束节点，退出循环
                if ("END".equals(currentNode.getNodeType())) {
                    execution.setOutput(currentNodeExecution.getNodeOutput());
                    execution.setStatus("SUCCESS");
                    break;
                }

                // 获取下一个节点
                currentNode = getNextNode(currentNode, edges, context, nodeMap);
            }

            // 更新执行记录
            execution.setEndTime(new Date());
            execution.setCostTime(System.currentTimeMillis() - startTime);
            executionMapper.updateById(execution);

            // 构建返回结果
            return buildResult(execution, executionId);

        } catch (Exception e) {
            log.error("工作流执行失败", e);
            WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
            if (execution != null) {
                execution.setStatus("FAILED");
                execution.setErrorMsg(e.getMessage());
                execution.setEndTime(new Date());
                execution.setCostTime(System.currentTimeMillis() - startTime);
                executionMapper.updateById(execution);
            }
            throw new RuntimeException("工作流执行失败: " + e.getMessage(), e);
        }
    }

    private WorkflowNode getNextNode(WorkflowNode currentNode, List<WorkflowEdge> edges,
                                     WorkflowContext context, Map<String, WorkflowNode> nodeMap) {
        List<WorkflowEdge> outgoingEdges = new ArrayList<WorkflowEdge>();
        for (WorkflowEdge edge : edges) {
            if (edge.getSourceId().equals(currentNode.getNodeId())) {
                outgoingEdges.add(edge);
            }
        }

        if (outgoingEdges.isEmpty()) {
            return null;
        }

        // 如果是条件节点，根据条件表达式选择分支
        if ("CONDITION".equals(currentNode.getNodeType())) {
            for (WorkflowEdge edge : outgoingEdges) {
                if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                    String condition = replaceVariables(edge.getCondition(), context);
                    Boolean result = evaluateSimpleExpression(condition);
                    if (result != null && result) {
                        return nodeMap.get(edge.getTargetId());
                    }
                }
            }
            // 默认分支
            return nodeMap.get(outgoingEdges.get(0).getTargetId());
        }

        // 普通节点，取第一个边
        return nodeMap.get(outgoingEdges.get(0).getTargetId());
    }

    private WorkflowExecution createExecutionRecord(String executionId, Workflow workflow,
                                                    WorkflowExecuteDTO dto) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutionId(executionId);
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowVersion(workflow.getVersion());
        execution.setSessionId(dto.getSessionId());
        execution.setStatus("RUNNING");
        execution.setStartTime(new Date());

        try {
            execution.setInput(objectMapper.writeValueAsString(dto.getInputs()));
        } catch (Exception e) {
            log.error("序列化输入失败", e);
        }

        return execution;
    }

    private WorkflowExecuteResultDTO buildResult(WorkflowExecution execution, String executionId) {
        WorkflowExecuteResultDTO result = new WorkflowExecuteResultDTO();
        result.setExecutionId(executionId);
        result.setStatus(execution.getStatus());
        result.setCostTime(execution.getCostTime());
        result.setErrorMsg(execution.getErrorMsg());

        try {
            if (execution.getOutput() != null && !execution.getOutput().isEmpty()) {
                result.setOutput(objectMapper.readValue(execution.getOutput(), Map.class));
            }
        } catch (Exception e) {
            log.error("解析输出失败", e);
        }

        // 获取节点执行记录
        List<WorkflowNodeExecution> nodeExecutions = nodeExecutionMapper.selectByExecutionId(executionId);
        List<WorkflowExecuteResultDTO.NodeExecutionResultDTO> nodeResults =
                new ArrayList<WorkflowExecuteResultDTO.NodeExecutionResultDTO>();

        for (WorkflowNodeExecution nodeExec : nodeExecutions) {
            WorkflowExecuteResultDTO.NodeExecutionResultDTO nodeResult =
                    new WorkflowExecuteResultDTO.NodeExecutionResultDTO();
            nodeResult.setNodeId(nodeExec.getNodeId());
            nodeResult.setNodeName(nodeExec.getNodeName());
            nodeResult.setNodeType(nodeExec.getNodeType());
            nodeResult.setStatus(nodeExec.getStatus());
            nodeResult.setCostTime(nodeExec.getCostTime());
            nodeResult.setErrorMsg(nodeExec.getErrorMsg());

            try {
                if (nodeExec.getNodeInput() != null && !nodeExec.getNodeInput().isEmpty()) {
                    nodeResult.setInput(objectMapper.readValue(nodeExec.getNodeInput(), Map.class));
                }
                if (nodeExec.getNodeOutput() != null && !nodeExec.getNodeOutput().isEmpty()) {
                    nodeResult.setOutput(objectMapper.readValue(nodeExec.getNodeOutput(), Map.class));
                }
            } catch (Exception e) {
                log.error("解析节点输入输出失败", e);
            }

            nodeResults.add(nodeResult);
        }

        result.setNodeResults(nodeResults);

        return result;
    }

    private Map<String, Object> parseConfig(String configJson) {
        try {
            if (configJson == null || configJson.isEmpty()) {
                return new HashMap<String, Object>();
            }
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.error("解析配置失败", e);
            return new HashMap<String, Object>();
        }
    }

    private String replaceVariables(String text, WorkflowContext context) {
        if (text == null) return null;
        String result = text;

        // 替换 {{input.xxx}}
        if (context.getInputs() != null) {
            for (Map.Entry<String, Object> entry : context.getInputs().entrySet()) {
                String key = entry.getKey();
                String value = String.valueOf(entry.getValue());
                result = result.replace("{{input." + key + "}}", value);
            }
        }

        // 替换 {{var.xxx}}
        if (context.getVariables() != null) {
            for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
                String key = entry.getKey();
                String value = String.valueOf(entry.getValue());
                result = result.replace("{{var." + key + "}}", value);
                result = result.replace("{{" + key + "}}", value);
            }
        }

        return result;
    }

    private Boolean evaluateSimpleExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }

        try {
            // 简单的表达式求值
            expression = expression.trim();

            // 处理等于比较
            if (expression.contains("==")) {
                String[] parts = expression.split("==");
                if (parts.length == 2) {
                    String left = parts[0].trim();
                    String right = parts[1].trim();
                    // 去除引号
                    if (right.startsWith("\"") && right.endsWith("\"")) {
                        right = right.substring(1, right.length() - 1);
                    }
                    return left.equals(right);
                }
            }

            // 处理大于比较
            if (expression.contains(">")) {
                String[] parts = expression.split(">");
                if (parts.length == 2) {
                    try {
                        double left = Double.parseDouble(parts[0].trim());
                        double right = Double.parseDouble(parts[1].trim());
                        return left > right;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }

            // 处理小于比较
            if (expression.contains("<")) {
                String[] parts = expression.split("<");
                if (parts.length == 2) {
                    try {
                        double left = Double.parseDouble(parts[0].trim());
                        double right = Double.parseDouble(parts[1].trim());
                        return left < right;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }

            // 处理布尔值
            if ("true".equalsIgnoreCase(expression)) {
                return true;
            }
            if ("false".equalsIgnoreCase(expression)) {
                return false;
            }

            return false;

        } catch (Exception e) {
            log.error("表达式求值失败: {}", expression, e);
            return false;
        }
    }

    @Override
    public SseEmitter streamExecuteWorkflow(WorkflowExecuteDTO dto) {
        final SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String executionId = UUID.randomUUID().toString();

                    // 发送开始事件
                    Map<String, Object> startData = new HashMap<String, Object>();
                    startData.put("executionId", executionId);
                    emitter.send(SseEmitter.event().name("start").data(startData));

                    // 获取工作流配置
                    Workflow workflow = workflowMapper.selectById(dto.getWorkflowId());
                    List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(dto.getWorkflowId());
                    List<WorkflowEdge> edges = edgeMapper.selectByWorkflowId(dto.getWorkflowId());

                    Map<String, WorkflowNode> nodeMap = new HashMap<String, WorkflowNode>();
                    for (WorkflowNode node : nodes) {
                        nodeMap.put(node.getNodeId(), node);
                    }

                    WorkflowContext context = new WorkflowContext();
                    context.setInputs(dto.getInputs());
                    context.setVariables(new HashMap<String, Object>());
                    context.setSessionId(dto.getSessionId());

                    WorkflowNode currentNode = null;
                    for (WorkflowNode node : nodes) {
                        if ("START".equals(node.getNodeType())) {
                            currentNode = node;
                            break;
                        }
                    }

                    while (currentNode != null) {
                        // 发送节点开始事件
                        Map<String, Object> nodeStartData = new HashMap<String, Object>();
                        nodeStartData.put("nodeId", currentNode.getNodeId());
                        nodeStartData.put("nodeName", currentNode.getName());
                        nodeStartData.put("nodeType", currentNode.getNodeType());
                        emitter.send(SseEmitter.event().name("node_start").data(nodeStartData));

                        // 执行节点
                        WorkflowNodeExecution nodeExec = executeNode(currentNode, context, executionId, nodeMap);

                        // 发送节点结束事件
                        Map<String, Object> nodeEndData = new HashMap<String, Object>();
                        nodeEndData.put("nodeId", currentNode.getNodeId());
                        nodeEndData.put("nodeName", currentNode.getName());
                        nodeEndData.put("status", nodeExec.getStatus());
                        nodeEndData.put("costTime", nodeExec.getCostTime());

                        if ("SUCCESS".equals(nodeExec.getStatus()) && nodeExec.getNodeOutput() != null) {
                            try {
                                nodeEndData.put("output", objectMapper.readValue(nodeExec.getNodeOutput(), Map.class));
                            } catch (Exception e) {
                                nodeEndData.put("output", nodeExec.getNodeOutput());
                            }
                        }
                        if (nodeExec.getErrorMsg() != null) {
                            nodeEndData.put("error", nodeExec.getErrorMsg());
                        }

                        emitter.send(SseEmitter.event().name("node_end").data(nodeEndData));

                        if (!"SUCCESS".equals(nodeExec.getStatus())) {
                            Map<String, Object> errorData = new HashMap<String, Object>();
                            errorData.put("error", nodeExec.getErrorMsg());
                            emitter.send(SseEmitter.event().name("error").data(errorData));
                            break;
                        }

                        if ("END".equals(currentNode.getNodeType())) {
                            emitter.send(SseEmitter.event().name("complete").data(nodeEndData.get("output")));
                            break;
                        }

                        currentNode = getNextNode(currentNode, edges, context, nodeMap);
                    }

                    emitter.complete();

                } catch (Exception e) {
                    log.error("流式执行失败", e);
                    try {
                        Map<String, Object> errorData = new HashMap<String, Object>();
                        errorData.put("error", e.getMessage());
                        emitter.send(SseEmitter.event().name("error").data(errorData));
                        emitter.completeWithError(e);
                    } catch (IOException ex) {
                        log.error("发送错误事件失败", ex);
                    }
                }
            }
        });

        return emitter;
    }

    @Override
    public List<WorkflowExecutionDTO> getExecutions(Long workflowId, int page, int size) {
        int offset = (page - 1) * size;
        List<WorkflowExecution> executions = executionMapper.selectByWorkflowIdWithPage(workflowId, offset, size);

        List<WorkflowExecutionDTO> result = new ArrayList<WorkflowExecutionDTO>();
        for (WorkflowExecution execution : executions) {
            WorkflowExecutionDTO dto = new WorkflowExecutionDTO();
            dto.setId(execution.getId());
            dto.setExecutionId(execution.getExecutionId());
            dto.setWorkflowId(execution.getWorkflowId());
            dto.setWorkflowVersion(execution.getWorkflowVersion());
            dto.setSessionId(execution.getSessionId());
            dto.setStatus(execution.getStatus());
            dto.setStartTime(execution.getStartTime());
            dto.setEndTime(execution.getEndTime());
            dto.setCostTime(execution.getCostTime());
            dto.setErrorMsg(execution.getErrorMsg());
            result.add(dto);
        }

        return result;
    }

    @Override
    public WorkflowExecuteResultDTO getExecutionDetail(String executionId) {
        WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
        if (execution == null) {
            throw new RuntimeException("执行记录不存在");
        }
        return buildResult(execution, executionId);
    }
}