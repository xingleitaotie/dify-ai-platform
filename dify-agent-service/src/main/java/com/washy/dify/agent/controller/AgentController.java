package com.washy.dify.agent.controller;

import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.agent.service.AgentConfigService;
import com.washy.dify.common.result.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Resource
    private AgentConfigService agentConfigService;

    @GetMapping("/list")
    public Result<List<AgentConfig>> list() {
        return Result.success(agentConfigService.list());
    }

    @GetMapping("/detail")
    public Result<AgentConfig> detail(@RequestParam Long id) {
        return Result.success(agentConfigService.getById(id));
    }

    @PostMapping("/create")
    public Result<Boolean> create(@RequestBody AgentConfig agent) {
        return Result.success(agentConfigService.save(agent));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody AgentConfig agent) {
        return Result.success(agentConfigService.updateById(agent));
    }

    @DeleteMapping("/delete")
    public Result<Boolean> delete(@RequestParam Long id) {
        return Result.success(agentConfigService.removeById(id));
    }
}