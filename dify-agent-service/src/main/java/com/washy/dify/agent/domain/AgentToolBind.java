package com.washy.dify.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_tool_bind")
public class AgentToolBind {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String toolName;
    private String toolType;
    private String toolDesc;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}