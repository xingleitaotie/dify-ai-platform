package com.washy.dify.workflow.service;

import com.washy.dify.common.entity.workflow.WorkflowCreateDTO;
import com.washy.dify.common.entity.workflow.WorkflowDetailDTO;
import com.washy.dify.common.entity.workflow.WorkflowListDTO;
import com.washy.dify.common.entity.workflow.WorkflowUpdateDTO;

import javax.validation.Valid;
import java.util.List;

public interface WorkflowService {
    WorkflowDetailDTO createWorkflow(@Valid WorkflowCreateDTO dto, String token);

    WorkflowDetailDTO updateWorkflow(Long id,WorkflowUpdateDTO dto, String token);

    List<WorkflowListDTO> listWorkflows(Long appId, String token);

    WorkflowDetailDTO getWorkflowDetail(Long id);

    Boolean deleteWorkflow(Long id, String token);

    Boolean publishWorkflow(Long id, String token);

}
