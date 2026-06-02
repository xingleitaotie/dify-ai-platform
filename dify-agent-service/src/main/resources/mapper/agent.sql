-- ----------------------------
-- Dify AI Agent 模块核心表
-- ----------------------------

-- 1. Agent 基础配置表
CREATE TABLE `agent_config` (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `agent_name` varchar(64) NOT NULL COMMENT 'Agent名称',
                                `agent_type` varchar(32) NOT NULL COMMENT 'Agent类型：function_call/react/zero_shot等',
                                `model_name` varchar(64) DEFAULT NULL COMMENT '关联大模型名称',
                                `temperature` decimal(10,2) DEFAULT '0.7' COMMENT '模型温度值',
                                `max_tokens` int(11) DEFAULT '2048' COMMENT '最大token数',
                                `system_prompt` text COMMENT '系统提示词',
                                `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0-禁用 1-启用',
                                `description` varchar(512) DEFAULT NULL COMMENT 'Agent描述',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
                                PRIMARY KEY (`id`) USING BTREE,
                                KEY `idx_agent_type` (`agent_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent基础配置表';

-- 2. Agent 绑定知识库表
CREATE TABLE `agent_kb_bind` (
                                 `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `agent_id` bigint(20) NOT NULL COMMENT 'Agent ID',
                                 `kb_id` varchar(64) NOT NULL COMMENT '知识库ID',
                                 `kb_name` varchar(64) DEFAULT NULL COMMENT '知识库名称',
                                 `retrieve_top_k` int(11) DEFAULT '5' COMMENT '检索topK数量',
                                 `score_threshold` decimal(10,2) DEFAULT '0.5' COMMENT '相似度阈值',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 UNIQUE KEY `uk_agent_kb` (`agent_id`,`kb_id`) USING BTREE,
                                 KEY `idx_agent_id` (`agent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent绑定知识库表';

-- 3. Agent 绑定工具表（绑定Function工具）
CREATE TABLE `agent_tool_bind` (
                                   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `agent_id` bigint(20) NOT NULL COMMENT 'Agent ID',
                                   `tool_name` varchar(64) NOT NULL COMMENT '工具名称（对应函数名）',
                                   `tool_type` varchar(32) NOT NULL COMMENT '工具类型：function/api/http等',
                                   `tool_desc` varchar(512) DEFAULT NULL COMMENT '工具描述',
                                   `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0-禁用 1-启用',
                                   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   UNIQUE KEY `uk_agent_tool` (`agent_id`,`tool_name`) USING BTREE,
                                   KEY `idx_agent_id` (`agent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent绑定工具表';

-- 4. Agent 对话记忆表
CREATE TABLE `agent_memory` (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `agent_id` bigint(20) NOT NULL COMMENT 'Agent ID',
                                `conversation_id` varchar(64) NOT NULL COMMENT '对话会话ID',
                                `user_query` text COMMENT '用户提问内容',
                                `ai_reply` text COMMENT 'AI回复内容',
                                `memory_type` varchar(32) DEFAULT 'short' COMMENT '记忆类型：short-短期 long-长期',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                KEY `idx_agent_id` (`agent_id`) USING BTREE,
                                KEY `idx_conversation_id` (`conversation_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent对话记忆表';