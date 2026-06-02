-- 用户表
CREATE TABLE sys_user (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
                          username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                          password VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
                          nickname VARCHAR(50) COMMENT '昵称',
                          email VARCHAR(100) COMMENT '邮箱',
                          phone VARCHAR(20) COMMENT '手机号',
                          status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 应用密钥表（API密钥管理）
CREATE TABLE sys_app (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
                         user_id BIGINT NOT NULL COMMENT '用户ID',
                         app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
                         app_key VARCHAR(64) NOT NULL UNIQUE COMMENT '应用密钥',
                         app_secret VARCHAR(64) NOT NULL COMMENT '应用密钥',
                         status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
                         create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         INDEX idx_user_id (user_id),
                         INDEX idx_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用密钥表';

-- 对话会话表
CREATE TABLE `chat_session` (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `session_id` varchar(100) NOT NULL COMMENT '会话ID',
                                `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
                                `message_count` int(11) DEFAULT 0 COMMENT '消息数量',
                                `status` tinyint(4) DEFAULT 1 COMMENT '状态：1-正常，0-删除',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_session_user` (`session_id`, `user_id`),
                                KEY `idx_user_id` (`user_id`),
                                KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE `chat_message` (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `session_id` varchar(100) NOT NULL COMMENT '会话ID',
                                `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                `role` varchar(20) NOT NULL COMMENT '角色：user/assistant/system',
                                `content` text NOT NULL COMMENT '消息内容',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_session_id` (`session_id`),
                                KEY `idx_user_id` (`user_id`),
                                KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';