package org.ticketing_system.biz.aiservice.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 反馈请求 DTO
 */
@Data
public class AiFeedbackReqDTO {

    @NotNull(message = "消息 ID 不能为空")
    private Long messageId;

    private Long sessionId;

    @NotNull(message = "反馈类型不能为空")
    private Integer feedbackType;

    private String feedbackContent;
}
