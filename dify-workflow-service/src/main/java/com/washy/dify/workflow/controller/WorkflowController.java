package com.washy.dify.workflow.controller;

import com.washy.dify.common.entity.workflow.*;
import com.washy.dify.common.result.Result;
import com.washy.dify.workflow.service.WorkflowExecuteService;
import com.washy.dify.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    
    private final WorkflowService workflowService;
    private final WorkflowExecuteService workflowExecuteService;
    
    // ========== 工作流管理 ==========
    
    @PostMapping("/create")
    public Result<WorkflowDetailDTO> create(@Valid @RequestBody WorkflowCreateDTO dto,
                                   @RequestHeader("token") String token) {
        return Result.success(workflowService.createWorkflow(dto, token));
    }
    
    @PutMapping("/update/{id}")
    public Result<WorkflowDetailDTO> update(@PathVariable Long id,
                                   @Valid @RequestBody WorkflowUpdateDTO dto,
                                   @RequestHeader("token") String token) {
        return Result.success(workflowService.updateWorkflow(id, dto, token));
    }
    
    @GetMapping("/list")
    public Result<List<WorkflowListDTO>> list(@RequestParam(required = false) Long appId,
                                       @RequestHeader("token") String token) {
        return Result.success(workflowService.listWorkflows(appId, token));
    }
    
    @GetMapping("/detail/{id}")
    public Result<WorkflowDetailDTO> detail(@PathVariable Long id) {
        return Result.success(workflowService.getWorkflowDetail(id));
    }
    
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id,
                                  @RequestHeader("token") String token) {
        return Result.success(workflowService.deleteWorkflow(id, token));
    }
    
    @PostMapping("/publish/{id}")
    public Result<Boolean> publish(@PathVariable Long id,
                                   @RequestHeader("token") String token) {
        return Result.success(workflowService.publishWorkflow(id, token));
    }
    
    // ========== 工作流执行 ==========
    
    @PostMapping("/execute")
    public Result<WorkflowExecuteResultDTO> execute(@Valid @RequestBody WorkflowExecuteDTO dto) {
        return Result.success(workflowExecuteService.executeWorkflow(dto));
    }
    
    @PostMapping("/stream/execute")
    public SseEmitter streamExecute(@Valid @RequestBody WorkflowExecuteDTO dto) {
        return workflowExecuteService.streamExecuteWorkflow(dto);
    }
    
    // ========== 执行历史 ==========
    
    @GetMapping("/executions")
    public Result<List<WorkflowExecutionDTO>> getExecutions(@RequestParam Long workflowId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(workflowExecuteService.getExecutions(workflowId, page, size));
    }
    
    @GetMapping("/execution/{executionId}")
    public Result<WorkflowExecuteResultDTO> getExecutionDetail(@PathVariable String executionId) {
        return Result.success(workflowExecuteService.getExecutionDetail(executionId));
    }
}