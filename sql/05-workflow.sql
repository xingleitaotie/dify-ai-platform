-- 工作流表
CREATE TABLE `workflow` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                            `name` VARCHAR(100) NOT NULL COMMENT '工作流名称',
                            `description` VARCHAR(500) COMMENT '工作流描述',
                            `app_id` BIGINT COMMENT '所属应用ID',
                            `user_id` BIGINT NOT NULL COMMENT '创建用户ID',
                            `version` INT DEFAULT 1 COMMENT '版本号',
                            `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT, PUBLISHED, ARCHIVED',
                            `graph` TEXT COMMENT '工作流图(JSON)',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            INDEX `idx_user_id` (`user_id`),
                            INDEX `idx_app_id` (`app_id`),
                            INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表';

-- 工作流节点表
CREATE TABLE `workflow_node` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `workflow_id` BIGINT NOT NULL,
                                 `node_id` VARCHAR(64) NOT NULL COMMENT '节点ID',
                                 `name` VARCHAR(100) NOT NULL COMMENT '节点名称',
                                 `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型',
                                 `config` TEXT COMMENT '节点配置(JSON)',
                                 `position` VARCHAR(200) COMMENT '位置信息(JSON)',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_workflow_node` (`workflow_id`, `node_id`),
                                 INDEX `idx_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点表';

-- 工作流边表
CREATE TABLE `workflow_edge` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `workflow_id` BIGINT NOT NULL,
                                 `source_id` VARCHAR(64) NOT NULL COMMENT '源节点ID',
                                 `target_id` VARCHAR(64) NOT NULL COMMENT '目标节点ID',
                                 `source_handle` VARCHAR(100) COMMENT '源节点处理点',
                                 `target_handle` VARCHAR(100) COMMENT '目标节点处理点',
                                 `condition` VARCHAR(500) COMMENT '条件表达式',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 INDEX `idx_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流边表';

-- 工作流执行记录表
CREATE TABLE `workflow_execution` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                                      `execution_id` VARCHAR(64) NOT NULL COMMENT '执行ID',
                                      `workflow_id` BIGINT NOT NULL,
                                      `workflow_version` INT DEFAULT 1,
                                      `session_id` VARCHAR(64) COMMENT '会话ID',
                                      `input` TEXT COMMENT '输入参数(JSON)',
                                      `output` TEXT COMMENT '输出结果(JSON)',
                                      `status` VARCHAR(20) DEFAULT 'RUNNING' COMMENT '状态: RUNNING, SUCCESS, FAILED, CANCELLED',
                                      `start_time` DATETIME NOT NULL,
                                      `end_time` DATETIME,
                                      `cost_time` BIGINT COMMENT '耗时(毫秒)',
                                      `error_msg` TEXT COMMENT '错误信息',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_execution_id` (`execution_id`),
                                      INDEX `idx_workflow_id` (`workflow_id`),
                                      INDEX `idx_session_id` (`session_id`),
                                      INDEX `idx_status` (`status`),
                                      INDEX `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录表';

-- 工作流节点执行记录表
CREATE TABLE `workflow_node_execution` (
                                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                                           `execution_id` VARCHAR(64) NOT NULL,
                                           `workflow_id` BIGINT NOT NULL,
                                           `node_id` VARCHAR(64) NOT NULL,
                                           `node_name` VARCHAR(100),
                                           `node_type` VARCHAR(50),
                                           `node_input` TEXT COMMENT '节点输入(JSON)',
                                           `node_output` TEXT COMMENT '节点输出(JSON)',
                                           `status` VARCHAR(20) DEFAULT 'RUNNING',
                                           `start_time` DATETIME NOT NULL,
                                           `end_time` DATETIME,
                                           `cost_time` BIGINT,
                                           `error_msg` TEXT,
                                           PRIMARY KEY (`id`),
                                           INDEX `idx_execution_id` (`execution_id`),
                                           INDEX `idx_workflow_id` (`workflow_id`),
                                           INDEX `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点执行记录表';