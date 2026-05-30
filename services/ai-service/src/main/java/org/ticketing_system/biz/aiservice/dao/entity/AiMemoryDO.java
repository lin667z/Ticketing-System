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
     * 内容
     */
    private String memoryContent;

    /**
     * 类型 0：短期 1：长期
     */
    private Integer memoryType;

    /**
     * 权重，越大优先级越高
     */
    private Integer weight;

    /**
     * 过期时间
     */
    private Date expireTime;
}
