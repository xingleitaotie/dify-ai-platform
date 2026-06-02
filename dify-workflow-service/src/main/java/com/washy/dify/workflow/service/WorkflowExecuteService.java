package com.washy.dify.workflow.service;

import com.washy.dify.common.entity.workflow.WorkflowExecuteDTO;
import com.washy.dify.common.entity.workflow.WorkflowExecuteResultDTO;
import com.washy.dify.common.entity.workflow.WorkflowExecutionDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;

public interface WorkflowExecuteService {
    WorkflowExecuteResultDTO executeWorkflow(@Valid WorkflowExecuteDTO dto);

    SseEmitter streamExecuteWorkflow(@Valid WorkflowExecuteDTO dto);

    List<WorkflowExecutionDTO> getExecutions(Long workflowId, int page, int size);

    WorkflowExecuteResultDTO getExecutionDetail(String executionId);

}
