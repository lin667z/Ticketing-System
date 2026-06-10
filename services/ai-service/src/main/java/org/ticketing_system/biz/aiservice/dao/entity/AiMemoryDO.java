package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;
import java.util.Date;

/**
 * AI 记忆实体
 */
@Data
@TableName("t_ai_memory")
public class AiMemoryDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 记忆键名
     */
    private String memoryKey;
    /**
     * 记忆内容
     */
    private String memoryContent;

    /**
     * 记忆类型
     */
    private Integer memoryType;

    /**
     * 权重
     */
    private Integer weight;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 偏好类型
     */
    private String preferenceType;

    /**
     * 偏好键名
     */
    private String preferenceKey;

    /**
     * 偏好值
     */
    private String preferenceValue;

    /**
     * 来源
     */
    private String source;
}
