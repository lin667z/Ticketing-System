package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * AI 客服反馈实体
 */
@Data
@TableName("t_ai_feedback")
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 消息 ID
     */
    private Long messageId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 反馈类型 0：点赞 1：点踩
     */
    private Integer feedbackType;

    /**
     * 反馈内容
     */
    private String feedbackContent;
}
