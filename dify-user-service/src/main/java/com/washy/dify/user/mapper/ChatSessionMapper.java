package com.washy.dify.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.user.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

    @Select("SELECT COUNT(*) FROM chat_session WHERE user_id = #{userId} AND status = 1")
    Integer countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND status = 1 ORDER BY update_time DESC LIMIT #{limit}")
    List<ChatSessionEntity> getRecentSessions(@Param("userId") Long userId, @Param("limit") Integer limit);

    @Update("UPDATE chat_session SET message_count = message_count + 1 WHERE session_id = #{sessionId}")
    void incrementMessageCount(@Param("sessionId") String sessionId);

}