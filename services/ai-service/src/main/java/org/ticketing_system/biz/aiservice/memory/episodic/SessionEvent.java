package org.ticketing_system.biz.aiservice.memory.episodic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 会话事件，记录单次用户行为或系统响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent {

    // 事件类型
    private SessionEventType eventType;

    // 事件时间戳
    private long timestamp;

    // 轮次编号
    private int turnNumber;

    // 事件负载数据
    @Builder.Default
    private Map<String, Object> payload = Map.of();

    /**
     * 快速构建会话事件
     */
    public static SessionEvent of(SessionEventType eventType, int turnNumber, Map<String, Object> payload) {
        return SessionEvent.builder()
                .eventType(eventType)
                .timestamp(System.currentTimeMillis())
                .turnNumber(turnNumber)
                .payload(payload == null ? Map.of() : payload)
                .build();
    }
}
