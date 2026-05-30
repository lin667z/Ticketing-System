package org.ticketing_system.biz.aiservice.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * AI 记忆响应
 */
@Data
public class AiMemoryRespDTO {

    /**
     * ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 记忆键
     */
    private String memoryKey;

    /**
     * 记忆内容
     */
    private String memoryContent;

    /**
     * 权重
     */
    private Integer weight;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
