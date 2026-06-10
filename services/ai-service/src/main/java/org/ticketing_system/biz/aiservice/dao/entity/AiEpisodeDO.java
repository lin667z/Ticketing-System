package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * AI 情节实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_episode")
public class AiEpisodeDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 事件内容
     */
    private String events;

    /**
     * 结构化事实
     */
    private String structuredFacts;

    /**
     * 摘要文本
     */
    private String summaryText;

    /**
     * 轮次开始
     */
    private Integer turnStart;

    /**
     * 轮次结束
     */
    private Integer turnEnd;
}
