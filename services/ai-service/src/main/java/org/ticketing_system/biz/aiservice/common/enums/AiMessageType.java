package org.ticketing_system.biz.aiservice.common.enums;

import java.util.Locale;

/**
 * AI 消息类型枚举
 */
public enum AiMessageType {

    /**
     * 系统消息
     */
    SYSTEM,
    /**
     * 用户消息
     */
    USER,
    /**
     * 助手消息
     */
    ASSISTANT,
    /**
     * 工具/函数调用消息
     */
    TOOL;

    /**
     * 获取角色小写名称
     */
    public String role() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 从角色名称转换
     */
    public static AiMessageType fromRole(String role) {
        if (role == null || role.isBlank()) {
            return USER;
        }
        return AiMessageType.valueOf(role.trim().toUpperCase(Locale.ROOT));
    }
}
