-- 提示词模板表
CREATE TABLE IF NOT EXISTS `prompt_template` (
                                                 `id` VARCHAR(36) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '模板名称',
    `version` VARCHAR(50) DEFAULT 'v1.0.0' COMMENT '版本号',
    `description` TEXT COMMENT '模板描述',
    `template_content` LONGTEXT NOT NULL COMMENT '提示词模板内容',
    `system_prompt` LONGTEXT COMMENT '系统提示词',
    `user_prompt_template` LONGTEXT COMMENT '用户提示词模板',
    `temperature` DECIMAL(3,2) DEFAULT 0.30 COMMENT '温度参数',
    `max_tokens` INT DEFAULT 2048 COMMENT '最大输出token数',
    `top_p` DECIMAL(3,2) DEFAULT 0.90 COMMENT 'Top P参数',
    `repeat_penalty` DECIMAL(3,2) DEFAULT 1.10 COMMENT '重复惩罚',
    `streaming` TINYINT(1) DEFAULT 0 COMMENT '是否流式输出',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT, ACTIVE, ARCHIVED',
    `type` VARCHAR(50) DEFAULT 'CUSTOM' COMMENT '模板类型: RAG, FUNCTION_CALLING, AGENT_DECISION, SUMMARY, CUSTOM',
    `category` VARCHAR(100) COMMENT '分类',
    `tags` VARCHAR(500) COMMENT '标签，逗号分隔',
    `created_by` VARCHAR(100) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(100) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `use_count` INT DEFAULT 0 COMMENT '使用次数',
    `rating` INT DEFAULT 0 COMMENT '评分(1-5)',
    PRIMARY KEY (`id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_type` (`type`),
    INDEX `idx_created_at` (`created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';

-- 提示词版本历史表
CREATE TABLE IF NOT EXISTS `prompt_version_history` (
                                                        `id` VARCHAR(36) NOT NULL COMMENT '主键ID',
    `template_id` VARCHAR(36) NOT NULL COMMENT '模板ID',
    `version` VARCHAR(50) NOT NULL COMMENT '版本号',
    `template_content` LONGTEXT NOT NULL COMMENT '模板内容',
    `change_log` TEXT COMMENT '变更日志',
    `created_by` VARCHAR(100) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_template_id` (`template_id`),
    INDEX `idx_version` (`version`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词版本历史表';

-- 提示词使用记录表
CREATE TABLE IF NOT EXISTS `prompt_usage_log` (
                                                  `id` VARCHAR(36) NOT NULL COMMENT '主键ID',
    `template_id` VARCHAR(36) COMMENT '模板ID',
    `template_name` VARCHAR(200) COMMENT '模板名称',
    `user_input` TEXT COMMENT '用户输入',
    `generated_prompt` LONGTEXT COMMENT '生成的提示词',
    `generation_method` VARCHAR(50) DEFAULT 'LLM' COMMENT '生成方式: LLM, TEMPLATE',
    `response_time_ms` INT COMMENT '响应时间(毫秒)',
    `confidence_score` INT COMMENT '置信度',
    `user_ip` VARCHAR(50) COMMENT '用户IP',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_template_id` (`template_id`),
    INDEX `idx_created_at` (`created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词使用记录表';

-- 插入一些初始数据
INSERT INTO `prompt_template` (`id`, `name`, `version`, `description`, `template_content`, `temperature`, `max_tokens`, `top_p`, `status`, `type`) VALUES
                                                                                                                                                       (UUID(), '知识库问答专家', 'v1.0.0', '基于本地知识库的专业问答助手', '# 角色\n你是一个专业的本地知识库问答助手...', 0.3, 2048, 0.9, 'ACTIVE', 'RAG'),
                                                                                                                                                       (UUID(), '文档摘要助手', 'v1.0.0', '文档内容智能摘要', '# 角色\n你是一个专业的文档摘要助手...', 0.3, 150, 0.9, 'ACTIVE', 'SUMMARY'),
                                                                                                                                                       (UUID(), '代码助手', 'v1.0.0', '代码编写和解释助手', '# 角色\n你是一个专业的代码助手...', 0.3, 2048, 0.9, 'ACTIVE', 'CUSTOM');