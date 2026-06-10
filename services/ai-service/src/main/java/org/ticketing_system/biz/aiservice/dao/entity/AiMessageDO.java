package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * AI 消息明细实体
 */
@Data
@TableName("t_ai_message")
public class AiMessageDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 角色 (user, assistant, system, tool)
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * Token 消耗
     */
    private Integer tokenCount;

    /**
     * 消息唯一标识(UUID)，前端 SSE 追踪用
     */
    private String messageUid;
}
