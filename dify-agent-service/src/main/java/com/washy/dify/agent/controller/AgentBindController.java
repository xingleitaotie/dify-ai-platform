package com.washy.dify.agent.controller;

import com.washy.dify.agent.domain.AgentKbBind;
import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.agent.domain.dto.AgentExecuteRequest;
import com.washy.dify.agent.service.AgentExecuteService;
import com.washy.dify.agent.service.AgentKbBindService;
import com.washy.dify.agent.service.AgentToolBindService;
import com.washy.dify.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentBindController {

    @Resource
    private AgentKbBindService kbBindService;

    @Resource
    private AgentToolBindService toolBindService;

    @Resource
    private AgentExecuteService agentExecuteService;

    // ====================== 知识库绑定 ======================
    @PostMapping("/bind/kb")
    public Result<AgentKbBind> bindKnowledgeBase(@RequestBody @Valid Map<String, Object> params) {
        try {
            Long agentId = Long.valueOf(params.get("agentId").toString());
            String kbId = params.get("kbId").toString();
            String kbName = (String) params.get("kbName");
            Integer retrieveTopK = params.get("retrieveTopK") != null ?
                    Integer.valueOf(params.get("retrieveTopK").toString()) : 5;
            BigDecimal scoreThreshold = params.get("scoreThreshold") != null ?
                    new BigDecimal(params.get("scoreThreshold").toString()) :
                    BigDecimal.valueOf(0.7);

            AgentKbBind bind = kbBindService.bindKnowledgeBase(
                    agentId, kbId, kbName, retrieveTopK, scoreThreshold
            );
            return Result.success(bind);
        } catch (Exception e) {
            log.error("绑定知识库失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/kb/list")
    public Result<List<AgentKbBind>> kbList(@RequestParam Long agentId) {
        List<AgentKbBind> list = kbBindService.getByAgentId(agentId);
        return Result.success(list);
    }

    // ====================== 工具绑定 ======================
    @PostMapping("/bind/tool")
    public Result<Boolean> bindTool(@RequestBody AgentToolBind bind) {
        return Result.success(toolBindService.save(bind));
    }

    @GetMapping("/tool/list")
    public Result<List<AgentToolBind>> toolList(@RequestParam Long agentId) {
        return Result.success(toolBindService.lambdaQuery()
                .eq(AgentToolBind::getAgentId, agentId).list());
    }

    @PostMapping("/execute")
    public Result<Object> executeAgent(@RequestBody AgentExecuteRequest request) {
        return Result.success(agentExecuteService.executeAgent(
                request.getAgentId(),
                request.getParams()
        ));
    }

    /**
     * 解绑工具
     */
    @DeleteMapping("/tool/unbind/{bindId}")
    public Result<Void> unbindTool(@PathVariable Long bindId) {
        boolean result = toolBindService.unbindTool(bindId);
        if (result) {
            return Result.success();
        } else {
            return Result.error("解绑失败");
        }
    }

    /**
     * 更新工具状态
     */
    @PutMapping("/tool/{bindId}/status")
    public Result<Void> updateToolStatus(
            @PathVariable Long bindId,
            @RequestParam Integer isEnabled) {
        boolean result = toolBindService.updateToolStatus(bindId, isEnabled);
        if (result) {
            return Result.success();
        } else {
            return Result.error("更新失败");
        }
    }

    // 2. 更新知识库绑定配置
    /**
     * 更新知识库绑定配置
     */
    @PutMapping("/kb/{bindId}")
    public Result<Void> updateKbBindConfig(
            @PathVariable Long bindId,
            @RequestBody Map<String, Object> params) {
        try {
            Integer retrieveTopK = params.get("retrieveTopK") != null ?
                    Integer.valueOf(params.get("retrieveTopK").toString()) : null;
            BigDecimal scoreThreshold = params.get("scoreThreshold") != null ?
                    new BigDecimal(params.get("scoreThreshold").toString()) : null;

            boolean result = kbBindService.updateBindConfig(bindId, retrieveTopK, scoreThreshold);
            if (result) {
                return Result.success();
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新绑定配置失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解绑知识库
     */
    @DeleteMapping("/kb/unbind/{bindId}")
    public Result<Void> unbindKb(@PathVariable Long bindId) {
        boolean result = kbBindService.unbindKnowledgeBase(bindId);
        if (result) {
            return Result.success();
        } else {
            return Result.error("解绑失败");
        }
    }
}