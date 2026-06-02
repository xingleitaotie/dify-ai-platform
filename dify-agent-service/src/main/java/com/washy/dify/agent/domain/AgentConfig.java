package com.washy.dify.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_config")
public class AgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentName;
    private String agentType;
    private String modelName;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private Integer isEnabled;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
}