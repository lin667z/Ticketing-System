package org.ticketing_system.biz.aiservice.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 * AI 记忆请求
 */
@Data
public class AiMemoryReqDTO {

    /**
     * 记忆键
     */
    @NotBlank(message = "记忆键不能为空")
    private String memoryKey;

    /**
     * 记忆内容
     */
    @NotBlank(message = "记忆内容不能为空")
    private String memoryContent;

    /**
     * 权重
     */
    private Integer weight;

    /**
     * 过期时间
     */
    private Date expireTime;
}
