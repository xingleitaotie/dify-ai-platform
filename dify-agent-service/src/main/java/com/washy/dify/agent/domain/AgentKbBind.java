package com.washy.dify.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_kb_bind")
public class AgentKbBind {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String kbId;
    private String kbName;
    private Integer retrieveTopK;
    private BigDecimal scoreThreshold;
    private LocalDateTime createTime;
}