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
