package com.washy.dify.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSessionEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sessionId;
    
    private Long userId;
    
    private String title;
    
    private Integer messageCount;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}