package com.washy.dify.workflow.node;

import com.washy.dify.workflow.config.WorkflowContext;
import java.util.Map;

/**
 * 节点执行器接口
 */
public interface NodeExecutor {
    
    /**
     * 执行节点
     * @param config 节点配置
     * @param context 工作流上下文
     * @return 执行结果
     */
    Map<String, Object> execute(Map<String, Object> config, WorkflowContext context);
    
    /**
     * 获取节点类型
     */
    String getNodeType();
}