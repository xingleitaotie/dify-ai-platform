package com.washy.dify.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessageEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sessionId;
    
    private Long userId;
    
    private String role;
    
    private String content;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}