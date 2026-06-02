package com.washy.dify.feign.client;

import com.washy.dify.common.entity.workflow.WorkflowExecuteDTO;
import com.washy.dify.common.entity.workflow.WorkflowExecuteResultDTO;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dify-workflow-service")
public interface WorkflowFeignClient {
    
    @PostMapping("/execute")
    Result<WorkflowExecuteResultDTO> execute(@RequestBody WorkflowExecuteDTO dto);
    
    @GetMapping("/detail/{id}")
    Result<Object> getDetail(@PathVariable("id") Long id);
}