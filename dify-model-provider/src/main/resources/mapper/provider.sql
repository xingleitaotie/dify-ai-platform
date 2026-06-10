-- schema.sql
CREATE DATABASE IF NOT EXISTS dify_model DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dify_model;

-- 模型供应商表
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
                              update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              UNIQUE KEY uk_provider_key (provider_key)
);

-- 模型配置表
CREATE TABLE llm_model_config (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                  provider_id BIGINT NOT NULL COMMENT '所属供应商ID',
                                  model_key VARCHAR(100) NOT NULL COMMENT '模型标识',
                                  model_name VARCHAR(200) NOT NULL COMMENT '模型显示名称',
                                  capability_type VARCHAR(50) NOT NULL COMMENT '能力类型: chat, embedding, rerank, stt, tts, vision',
                                  model_schema VARCHAR(50) DEFAULT 'openai' COMMENT '协议类型',
                                  context_length INT DEFAULT 4096 COMMENT '上下文长度',
                                  dimension INT COMMENT '向量维度',
                                  sort_order INT DEFAULT 0,
                                  status TINYINT DEFAULT 1,
                                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  FOREIGN KEY (provider_id) REFERENCES llm_provider(id),
                                  UNIQUE KEY uk_provider_model (provider_id, model_key, capability_type)
);

-- 系统能力配置表
CREATE TABLE llm_system_capability (
                                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       capability_type VARCHAR(50) NOT NULL COMMENT '能力类型',
                                       model_config_id BIGINT COMMENT '使用的模型配置ID',
                                       fallback_config_id BIGINT COMMENT '备用配置ID',
                                       update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       UNIQUE KEY uk_capability (capability_type),
                                       FOREIGN KEY (model_config_id) REFERENCES llm_model_config(id),
                                       FOREIGN KEY (fallback_config_id) REFERENCES llm_model_config(id)
);

-- 初始化供应商数据
INSERT INTO llm_provider (provider_key, provider_name, base_url, status) VALUES
                                                                             ('ollama', 'Ollama', 'http://localhost:11434', 1),
                                                                             ('openai', 'OpenAI', 'https://api.openai.com/v1', 1),
                                                                             ('modelscope', 'ModelScope', 'https://api.modelscope.cn/v1', 1),
                                                                             ('aliyun', '阿里云(通义千问)', 'https://dashscope.aliyuncs.com/api/v1', 1),
                                                                             ('baidu', '百度文心一言', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat', 1),
                                                                             ('xfyun', '讯飞星火', 'https://spark-api.xf-yun.com/v3.5/chat', 1),
                                                                             ('zhipu', '智谱AI', 'https://open.bigmodel.cn/api/paas/v4', 1);

-- 初始化系统能力配置
INSERT INTO llm_system_capability (capability_type) VALUES
                                                        ('chat'), ('embedding'), ('rerank'), ('stt'), ('tts'), ('vision');