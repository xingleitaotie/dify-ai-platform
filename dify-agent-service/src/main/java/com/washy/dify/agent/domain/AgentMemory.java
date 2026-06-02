package com.washy.dify.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_memory")
public class AgentMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String conversationId;
    private String userQuery;
    private String aiReply;
    private String memoryType;
    private LocalDateTime createTime;
}