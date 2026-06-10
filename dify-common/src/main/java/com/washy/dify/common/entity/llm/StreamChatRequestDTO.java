package com.washy.dify.common.entity.llm;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 流式对话请求DTO
 */
@Data
public class StreamChatRequestDTO {
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    private String sessionId;
    
    private String modelType;        // 模型类型
    
    private Long configId;           // 配置ID
    
    private Double temperature;      // 温度参数（覆盖默认值）
    
    private Integer maxTokens;       // 最大token（覆盖默认值）

    private List<ChatMessage> messages;

    // 新增：意图类型（code、chat、rag、creative、data、streaming、general）
    private String intent;

    // 新增：是否保存上下文
    private Boolean saveContext = true;

}