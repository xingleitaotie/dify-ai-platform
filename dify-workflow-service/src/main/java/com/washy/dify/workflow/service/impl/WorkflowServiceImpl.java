package com.washy.dify.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.entity.workflow.*;
import com.washy.dify.common.exception.BusinessException;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.workflow.entity.Workflow;
import com.washy.dify.workflow.entity.WorkflowEdge;
import com.washy.dify.workflow.entity.WorkflowNode;
import com.washy.dify.workflow.mapper.WorkflowEdgeMapper;
import com.washy.dify.workflow.mapper.WorkflowMapper;
import com.washy.dify.workflow.mapper.WorkflowNodeMapper;
import com.washy.dify.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowDetailDTO createWorkflow(WorkflowCreateDTO dto, String token) {
        Long userId = 1L;

        Workflow workflow = new Workflow();
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setAppId(dto.getAppId());
        workflow.setUserId(userId);
        workflow.setVersion(1);
        workflow.setStatus("DRAFT");
        workflow.setCreateTime(new Date());
        workflow.setUpdateTime(new Date());

        if (dto.getGraph() != null) {
            try {
                workflow.setGraph(objectMapper.writeValueAsString(dto.getGraph()));
            } catch (Exception e) {
                log.error("序列化图数据失败", e);
            }
        }

        workflowMapper.insert(workflow);

        if (dto.getGraph() != null) {
            saveNodesAndEdges(workflow.getId(), dto.getGraph());
        }

        return getWorkflowDetail(workflow.getId());
    }

    @Override
    @Transactional
    public WorkflowDetailDTO updateWorkflow(Long id, WorkflowUpdateDTO dto, String token) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND);
        }

        if (dto.getName() != null) {
            workflow.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            workflow.setDescription(dto.getDescription());
        }
        workflow.setUpdateTime(new Date());

        if (dto.getGraph() != null) {
            try {
                workflow.setGraph(objectMapper.writeValueAsString(dto.getGraph()));
            } catch (Exception e) {
                log.error("序列化图数据失败", e);
            }

            nodeMapper.deleteByWorkflowId(workflow.getId());
            edgeMapper.deleteByWorkflowId(workflow.getId());
            saveNodesAndEdges(workflow.getId(), dto.getGraph());
        }

        workflowMapper.updateById(workflow);

        return getWorkflowDetail(workflow.getId());
    }

    private void saveNodesAndEdges(Long workflowId, WorkflowGraphDTO graph) {
        Date now = new Date();

        if (graph.getNodes() != null) {
            for (WorkflowNodeDTO nodeDTO : graph.getNodes()) {
                WorkflowNode node = new WorkflowNode();
                node.setWorkflowId(workflowId);
                node.setNodeId(nodeDTO.getId());
                node.setName(nodeDTO.getName());
                node.setNodeType(nodeDTO.getType());
                node.setCreateTime(now);
                node.setUpdateTime(now);

                try {
                    if (nodeDTO.getConfig() != null) {
                        node.setConfig(objectMapper.writeValueAsString(nodeDTO.getConfig()));
                    }
                    if (nodeDTO.getPosition() != null) {
                        node.setPosition(objectMapper.writeValueAsString(nodeDTO.getPosition()));
                    }
                } catch (Exception e) {
                    log.error("序列化节点配置失败", e);
                }

                nodeMapper.insert(node);
            }
        }

        if (graph.getEdges() != null) {
            for (WorkflowEdgeDTO edgeDTO : graph.getEdges()) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setWorkflowId(workflowId);
                edge.setSourceId(edgeDTO.getSource());
                edge.setTargetId(edgeDTO.getTarget());
                edge.setSourceHandle(edgeDTO.getSourceHandle());
                edge.setTargetHandle(edgeDTO.getTargetHandle());
                edge.setCondition(edgeDTO.getCondition());
                edge.setCreateTime(now);
                edgeMapper.insert(edge);
            }
        }
    }

    @Override
    public List<WorkflowListDTO> listWorkflows(Long appId, String token) {
        Long userId = 1L;

        List<Workflow> workflows;
        if (appId != null) {
            workflows = workflowMapper.selectByAppId(appId);
        } else {
            workflows = workflowMapper.selectByUserId(userId);
        }

        if (workflows == null) {
            return new ArrayList<>();
        }

        return workflows.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowDetailDTO getWorkflowDetail(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND);
        }

        // 手动复制基本属性，避免类型转换错误
        WorkflowDetailDTO detailDTO = new WorkflowDetailDTO();
        detailDTO.setId(workflow.getId());
        detailDTO.setName(workflow.getName());
        detailDTO.setDescription(workflow.getDescription());
        detailDTO.setAppId(workflow.getAppId());
        detailDTO.setUserId(workflow.getUserId());
        detailDTO.setVersion(workflow.getVersion());
        detailDTO.setStatus(workflow.getStatus());
        detailDTO.setCreateTime(workflow.getCreateTime());
        detailDTO.setUpdateTime(workflow.getUpdateTime());

        // 解析图数据
        if (workflow.getGraph() != null && !workflow.getGraph().isEmpty()) {
            try {
                WorkflowGraphDTO graph = objectMapper.readValue(workflow.getGraph(), WorkflowGraphDTO.class);
                detailDTO.setGraph(graph);
            } catch (Exception e) {
                log.error("解析图数据失败", e);
                detailDTO.setGraph(buildGraphFromDB(id));
            }
        } else {
            detailDTO.setGraph(buildGraphFromDB(id));
        }

        return detailDTO;
    }

    private WorkflowGraphDTO buildGraphFromDB(Long workflowId) {
        WorkflowGraphDTO graph = new WorkflowGraphDTO();

        List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(workflowId);
        List<WorkflowNodeDTO> nodeDTOs = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            WorkflowNodeDTO nodeDTO = new WorkflowNodeDTO();
            nodeDTO.setId(node.getNodeId());
            nodeDTO.setName(node.getName());
            nodeDTO.setType(node.getNodeType());

            try {
                if (node.getConfig() != null && !node.getConfig().isEmpty()) {
                    nodeDTO.setConfig(objectMapper.readValue(node.getConfig(), Map.class));
                }
                if (node.getPosition() != null && !node.getPosition().isEmpty()) {
                    nodeDTO.setPosition(objectMapper.readValue(node.getPosition(), WorkflowNodeDTO.Position.class));
                }
            } catch (Exception e) {
                log.error("解析节点数据失败", e);
            }

            nodeDTOs.add(nodeDTO);
        }
        graph.setNodes(nodeDTOs);

        List<WorkflowEdge> edges = edgeMapper.selectByWorkflowId(workflowId);
        List<WorkflowEdgeDTO> edgeDTOs = new ArrayList<>();
        for (WorkflowEdge edge : edges) {
            WorkflowEdgeDTO edgeDTO = new WorkflowEdgeDTO();
            edgeDTO.setId(String.valueOf(edge.getId()));
            edgeDTO.setSource(edge.getSourceId());
            edgeDTO.setTarget(edge.getTargetId());
            edgeDTO.setSourceHandle(edge.getSourceHandle());
            edgeDTO.setTargetHandle(edge.getTargetHandle());
            edgeDTO.setCondition(edge.getCondition());
            edgeDTOs.add(edgeDTO);
        }
        graph.setEdges(edgeDTOs);

        return graph;
    }

    @Override
    @Transactional
    public Boolean deleteWorkflow(Long id, String token) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            return false;
        }

        workflow.setStatus("ARCHIVED");
        workflow.setUpdateTime(new Date());
        workflowMapper.updateById(workflow);

        return true;
    }

    @Override
    @Transactional
    public Boolean publishWorkflow(Long id, String token) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND);
        }

        workflow.setStatus("PUBLISHED");
        workflow.setVersion(workflow.getVersion() + 1);
        workflow.setUpdateTime(new Date());
        workflowMapper.updateById(workflow);

        return true;
    }

    private WorkflowListDTO convertToListDTO(Workflow workflow) {
        WorkflowListDTO dto = new WorkflowListDTO();
        dto.setId(workflow.getId());
        dto.setName(workflow.getName());
        dto.setDescription(workflow.getDescription());
        dto.setVersion(workflow.getVersion());
        dto.setStatus(workflow.getStatus());
        dto.setCreateTime(workflow.getCreateTime());
        dto.setUpdateTime(workflow.getUpdateTime());
        return dto;
    }
}