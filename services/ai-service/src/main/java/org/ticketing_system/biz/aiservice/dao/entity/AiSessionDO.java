package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * AI 会话实体
 */
@Data
@TableName("t_ai_session")
public class AiSessionDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 状态 0：进行中 1：已结束
     */
    private Integer status;

    /**
     * 上下文摘要
     */
    private String summary;
}
