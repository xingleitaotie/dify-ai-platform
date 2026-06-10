package com.washy.dify.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.provider.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {

    @Select("SELECT * FROM llm_model_config WHERE capability_type = #{capabilityType} AND status = 1")
    List<ModelConfigEntity> selectByCapabilityType(@Param("capabilityType") String capabilityType);

    @Select("SELECT mc.*, p.provider_key, p.provider_name, p.base_url, p.api_key, p.secret " +
            "FROM llm_model_config mc " +
            "LEFT JOIN llm_provider p ON mc.provider_id = p.id " +
            "WHERE mc.id = #{id}")
    ModelConfigEntity selectWithProviderById(@Param("id") Long id);
}