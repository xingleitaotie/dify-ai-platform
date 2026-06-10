package com.washy.dify.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.result.Result;
import com.washy.dify.common.result.ResultCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * 网关限流配置
 */
@Configuration
public class SentinelConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 初始化限流规则
        initGatewayRules();
        // 初始化限流返回结果
        initBlockHandler();
    }

    /**
     * 配置限流规则：IP限流 + 接口限流
     */
    private void initGatewayRules() {
        // 🔥 修复：必须使用 Set 而不是 List
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 1. LLM接口限流：每秒10次请求
        rules.add(new GatewayFlowRule("dify-llm-service")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 2. RAG接口限流：每秒5次请求
        rules.add(new GatewayFlowRule("dify-rag-service")
                .setCount(5)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 3. 函数接口限流：每秒8次请求
        rules.add(new GatewayFlowRule("dify-function-service")
                .setCount(8)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 4. Agent接口限流：每秒10次请求
        rules.add(new GatewayFlowRule("dify-agent-service")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 5. Prompt接口限流：每秒10次请求
        rules.add(new GatewayFlowRule("dify-prompt-engine")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 6. 工作流接口限流：每秒10次请求
        rules.add(new GatewayFlowRule("dify-workflow-service")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 7. 模型的统一配置：每秒10次请求
        rules.add(new GatewayFlowRule("dify-model-provider")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));
        // 加载规则（现在完全匹配：Set<GatewayFlowRule>）
        GatewayRuleManager.loadRules(rules);
    }

    /**
     * 限流自定义返回结果
     */
    private void initBlockHandler() {
        BlockRequestHandler handler = (exchange, t) -> {
            Result<Object> result = Result.failed(ResultCode.FAILED);
            result.setMsg("请求频繁，请稍后再试");

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(result);
        };
        GatewayCallbackManager.setBlockHandler(handler);
    }
}