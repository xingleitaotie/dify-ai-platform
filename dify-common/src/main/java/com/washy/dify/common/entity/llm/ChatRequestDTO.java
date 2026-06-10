package com.washy.dify.common.entity.llm;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 通用对话请求DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 标准消息列表（推荐使用）
     */
    private List<ChatMessage> messages;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;          // 用户消息
    
    private String sessionId;        // 会话ID（用于上下文记忆）
    
    private String modelType;        // 模型类型：ollama/openai/modelScope/qwen/ernie/spark/zhipu
    
    private Long configId;           // 配置ID（优先于modelType）
    
    private Map<String, Object> params;  // 额外参数（temperature, maxTokens等）
    
    private List<ChatMessage> history;   // 历史消息（可选）

    // 新增：意图类型（code、chat、rag、creative、data、streaming、general）
    private String intent;

    // 新增：是否保存上下文
    private Boolean saveContext = true;

}

