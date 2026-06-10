package com.washy.dify.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateEntity> {

    /**
     * 根据类型获取活跃的模板
     */
    @Select("SELECT * FROM prompt_template WHERE type = #{type} AND status = 'ACTIVE' ORDER BY use_count DESC")
    List<PromptTemplateEntity> getActiveTemplatesByType(@Param("type") String type);

    /**
     * 增加使用次数
     */
    @Update("UPDATE prompt_template SET use_count = use_count + 1 WHERE id = #{id}")
    void incrementUseCount(@Param("id") String id);

    /**
     * 搜索模板
     */
    @Select("SELECT * FROM prompt_template WHERE status = 'ACTIVE' AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))")
    List<PromptTemplateEntity> searchTemplates(@Param("keyword") String keyword);

    /**
     * 根据模板类型查询已关联的向量库名称（取最新的一条ACTIVE模板）
     * @param type 模板类型（code、rag、general等）
     * @return 向量库名称，如果没有则返回null
     */
    @Select("SELECT vector_store_name FROM prompt_template WHERE type = #{type} AND status = 'ACTIVE' AND vector_store_name IS NOT NULL AND vector_store_name != '' LIMIT 1")
    String selectVectorStoreByType(@Param("type") String type);
}