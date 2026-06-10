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

-- 1. 模型供应商表 (provider)
CREATE TABLE llm_provider (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              provider_key VARCHAR(50) NOT NULL COMMENT '供应商标识: ollama, openai, modelscope, aliyun, baidu, xfyun',
                              provider_name VARCHAR(100) NOT NULL COMMENT '供应商名称',
                              base_url VARCHAR(500) COMMENT 'API地址',
                              api_key VARCHAR(500) COMMENT 'API密钥',
                              secret VARCHAR(500) COMMENT '密钥/Secret',
                              description VARCHAR(500) COMMENT '描述',
                              sort_order INT DEFAULT 0 COMMENT '排序',
                              status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用,1-启用',
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                              update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 模型配置表 (支持多种能力类型)
CREATE TABLE llm_model_config (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                  provider_id BIGINT NOT NULL COMMENT '所属供应商ID',
                                  model_key VARCHAR(100) NOT NULL COMMENT '模型标识',
                                  model_name VARCHAR(200) NOT NULL COMMENT '模型显示名称',
                                  capability_type VARCHAR(50) NOT NULL COMMENT '能力类型: chat, embedding, rerank, stt, tts, vision',
                                  model_schema VARCHAR(50) DEFAULT 'openai' COMMENT '协议类型: openai, dashscope, ernie, spark, custom',
                                  model_params JSON COMMENT '模型参数配置',
                                  context_length INT DEFAULT 4096 COMMENT '上下文长度(仅chat)',
                                  dimension INT COMMENT '向量维度(仅embedding)',
                                  is_system TINYINT DEFAULT 0 COMMENT '是否系统内置: 0-否,1-是',
                                  sort_order INT DEFAULT 0,
                                  status TINYINT DEFAULT 1,
                                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  FOREIGN KEY (provider_id) REFERENCES llm_provider(id),
                                  UNIQUE KEY uk_provider_model (provider_id, model_key, capability_type)
);

-- 3. 系统能力配置表 (选择哪个配置作为默认)
CREATE TABLE llm_system_capability (
                                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       capability_type VARCHAR(50) NOT NULL COMMENT '能力类型',
                                       model_config_id BIGINT COMMENT '使用的模型配置ID',
                                       fallback_config_id BIGINT COMMENT '备用配置ID',
                                       config_params JSON COMMENT '能力配置参数',
                                       update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       UNIQUE KEY uk_capability (capability_type),
                                       FOREIGN KEY (model_config_id) REFERENCES llm_model_config(id),
                                       FOREIGN KEY (fallback_config_id) REFERENCES llm_model_config(id)
);

-- 4. 初始化供应商数据
INSERT INTO llm_provider (provider_key, provider_name, base_url, status) VALUES
                                                                             ('ollama', 'Ollama', 'http://localhost:11434', 1),
                                                                             ('openai', 'OpenAI', 'https://api.openai.com/v1', 1),
                                                                             ('modelscope', 'ModelScope', 'https://api.modelscope.cn/v1', 1),
                                                                             ('aliyun', '阿里云(通义千问)', 'https://dashscope.aliyuncs.com/api/v1', 1),
                                                                             ('baidu', '百度文心一言', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat', 1),
                                                                             ('xfyun', '讯飞星火', 'https://spark-api.xf-yun.com/v3.5/chat', 1),
                                                                             ('zhipu', '智谱AI', 'https://open.bigmodel.cn/api/paas/v4', 1);

-- 5. 初始化系统能力配置 (默认使用OpenAI协议)
INSERT INTO llm_system_capability (capability_type) VALUES
                                                        ('chat'), ('embedding'), ('rerank'), ('stt'), ('tts'), ('vision');