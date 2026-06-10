USE
ticketing_system_ai;

CREATE TABLE `t_ai_session`
(
    `id`          bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`     bigint(20) DEFAULT NULL COMMENT '用户ID',
    `title`       varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
    `status`      int(3) DEFAULT '0' COMMENT '会话状态 0：进行中 1：已结束',
    `summary`     text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话上下文摘要',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服会话表';

CREATE TABLE `t_ai_message`
(
    `id`          bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `session_id`  bigint(20) DEFAULT NULL COMMENT '会话ID',
    `user_id`     bigint(20) DEFAULT NULL COMMENT '用户ID',
    `role`        varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色(user, assistant, system, tool)',
    `content`     text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消息内容',
    `model_name`  varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型名称',
    `token_count` int(11) DEFAULT '0' COMMENT 'Token消耗数',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服消息明细表';

CREATE TABLE `t_ai_memory`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`        bigint(20) DEFAULT NULL COMMENT '用户ID',
    `memory_key`     varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记忆分类/键名',
    `memory_content` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记忆内容',
    `memory_type`    int(3) DEFAULT '0' COMMENT '记忆类型 0：短期/会话记忆 1：长期/用户画像',
    `weight`         int(11) DEFAULT '0' COMMENT '权重/优先级，数值越大越优先注入上下文',
    `expire_time`    datetime DEFAULT NULL COMMENT '过期时间(为空则长期有效)',
    `create_time`    datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服记忆表';

CREATE TABLE `t_ai_feedback`
(
    `id`               bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `message_id`       bigint(20) DEFAULT NULL COMMENT '消息ID',
    `user_id`          bigint(20) DEFAULT NULL COMMENT '用户ID',
    `feedback_type`    int(3) DEFAULT NULL COMMENT '反馈类型 0：点赞 1：点踩',
    `feedback_content` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '反馈详细内容',
    `create_time`      datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`      datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`         tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_message_id` (`message_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服用户反馈表';

CREATE TABLE `t_ai_tool_log`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `message_id`      bigint(20) DEFAULT NULL COMMENT '触发调用的消息ID',
    `session_id`      bigint(20) DEFAULT NULL COMMENT '所属会话ID',
    `tool_name`       varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具/函数名称',
    `tool_params`     text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具调用参数(JSON)',
    `tool_result`     text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具执行结果(JSON)',
    `execute_time_ms` bigint(20) DEFAULT NULL COMMENT '执行耗时(ms)',
    `status`          int(3) DEFAULT '0' COMMENT '执行状态 0：成功 1：失败',
    `error_msg`       text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
    `create_time`     datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服函数调用日志表';

CREATE TABLE `t_ai_episode`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`         bigint(20) DEFAULT NULL COMMENT '用户ID',
    `session_id`      bigint(20) DEFAULT NULL COMMENT '会话ID',
    `events`          json DEFAULT NULL COMMENT '域事件数组 [{eventType, timestamp, turnNumber, payload}, ...]',
    `structured_facts` json DEFAULT NULL COMMENT '确定性派生的事实列表 ["查询了北京→上海 2026-06-05", ...]',
    `summary_text`    text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '可选 LLM 自然语言摘要',
    `turn_start`      int(11) DEFAULT NULL COMMENT '起始轮次',
    `turn_end`        int(11) DEFAULT NULL COMMENT '结束轮次',
    `create_time`     datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) USING BTREE,
    KEY `idx_session_id` (`session_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服情景摘要表（Event Sourcing）';

-- v4.0 Migration: ALTER t_ai_memory for L4a structured preferences
ALTER TABLE `t_ai_memory`
    ADD COLUMN IF NOT EXISTS `preference_type` VARCHAR(32) DEFAULT NULL COMMENT '偏好类型：ROUTE/TIME_WINDOW/SEAT_CLASS/BUDGET/TRAIN_TYPE/STATION/CUSTOM',
    ADD COLUMN IF NOT EXISTS `preference_key`  VARCHAR(128) DEFAULT NULL COMMENT '偏好键名，如 morning_departure',
    ADD COLUMN IF NOT EXISTS `preference_value` VARCHAR(256) DEFAULT NULL COMMENT '偏好值，如 06:00-09:00',
    ADD COLUMN IF NOT EXISTS `source`         VARCHAR(32) DEFAULT 'MANUAL' COMMENT '来源：MANUAL/AUTO_DIGEST/EPISODE_EXTRACT';

-- v4.0 Migration: ALTER t_ai_message for message tracking
ALTER TABLE `t_ai_message`
    ADD COLUMN IF NOT EXISTS `message_uid` VARCHAR(64) DEFAULT NULL COMMENT '消息唯一标识(UUID)，前端 SSE 追踪用',
    ADD KEY IF NOT EXISTS `idx_user_id` (`user_id`) USING BTREE;

-- v4.0 Migration: ALTER t_ai_feedback for session association
ALTER TABLE `t_ai_feedback`
    ADD COLUMN IF NOT EXISTS `session_id` bigint(20) DEFAULT NULL COMMENT '所属会话ID',
    ADD KEY IF NOT EXISTS `idx_user_id` (`user_id`) USING BTREE;

-- v5.0 Migration: L5 知识/RAG 预留表（铁路规则检索）
CREATE TABLE IF NOT EXISTS `t_ai_knowledge`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `category`       varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则分类：退改签/计价/证件/儿童票/乘车规则',
    `title`          varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则标题',
    `content`        text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则正文',
    `keywords`       varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '降级关键词检索用关键词',
    `embedding`      blob DEFAULT NULL COMMENT '向量(预留，或落外部向量库)',
    `effective_date` datetime DEFAULT NULL COMMENT '规则生效时间',
    `expire_date`    datetime DEFAULT NULL COMMENT '规则失效时间(为空则长期有效)',
    `version`        varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则版本',
    `source`         varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源',
    `weight`         int(11) DEFAULT '0' COMMENT '权重/优先级，数值越大越优先注入上下文',
    `create_time`    datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT '0' COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服铁路规则知识表（L5 RAG 预留）';
