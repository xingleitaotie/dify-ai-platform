-- 大模型配置表
CREATE TABLE `llm_config` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `config_name` varchar(100) NOT NULL COMMENT '配置名称',
                              `type` varchar(50) NOT NULL COMMENT '模型类型：ollama/openai/qwen/ernie/spark/zhipu/modelScope',
                              `model_name` varchar(200) NOT NULL COMMENT '模型名称',
                              `base_url` varchar(500) NOT NULL COMMENT 'API地址',
                              `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥',
                              `secret` varchar(500) DEFAULT NULL COMMENT '密钥',
                              `max_tokens` int DEFAULT '2048' COMMENT '最大token',
                              `temperature` decimal(3,2) DEFAULT '0.70' COMMENT '温度参数',
                              `timeout` int DEFAULT '60' COMMENT '超时时间',
                              `is_default` tinyint DEFAULT '0' COMMENT '是否默认配置',
                              `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型配置表';

-- 插入示例数据
INSERT INTO `llm_config` (`config_name`, `type`, `model_name`, `base_url`, `api_key`, `is_default`, `status`) VALUES
    ('ModelScope默认配置', 'modelScope', 'unsloth/DeepSeek-R1-0528-Qwen3-8B-GGUF', 'https://ms-ens-298b7a7b-3c0b.api-inference.modelscope.cn/v1', 'ms-190914bc-6125-4840-9347-8366e74399bc', 1, 1);