package com.washy.dify.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.user.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessageEntity> getMessagesBySessionId(@Param("sessionId") String sessionId);
}
