package org.ticketing_system.biz.aiservice.dto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;

import java.util.Date;
import java.util.Map;
/**
 * AI 聊天消息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    /**
     * 消息 ID
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
     * 消息类型
     */
    private AiMessageType messageType;

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
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 构建用户消息
     */
    public static AiChatMessage user(Long sessionId, Long userId, String content) {
        return AiChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .messageType(AiMessageType.USER)
                .content(content)
                .build();
    }
}
