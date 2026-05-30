package org.ticketing_system.biz.aiservice.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 聊天请求
 */
@Data
public class AiChatReqDTO {

    /**
     * 会话 ID，首条消息为空
     */
    private Long sessionId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 启用工具调用
     */
    private Boolean enableTools;
}
