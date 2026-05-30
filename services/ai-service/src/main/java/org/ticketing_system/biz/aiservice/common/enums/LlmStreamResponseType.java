package org.ticketing_system.biz.aiservice.common.enums;

/**
 * LLM 流式响应类型枚举
 */
public enum LlmStreamResponseType {
    /**
     * 普通文本内容分片
     */
    CONTENT,
    /**
     * 响应流结束
     */
    FINISH,
    /**
     * 重试
     */
    RETRYING
}
