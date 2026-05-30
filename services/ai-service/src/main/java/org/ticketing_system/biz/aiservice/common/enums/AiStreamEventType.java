package org.ticketing_system.biz.aiservice.common.enums;

/**
 * AI 流式响应事件类型
 */
public enum AiStreamEventType {
    /**
     * 普通对话片段
     */
    CHAT_CHUNK,
    /**
     * 反问用户（需要更多信息）
     */
    ASK_USER,
    /**
     * 异常发生
     */
    ERROR,
    /**
     * 流式响应正常结束
     */
    DONE,
    /**
     * 故障转移重试中
     */
    RETRYING,
    TRACE,
    TOOL_START,
    TOOL_END,
    /**
     * 结构化组件数据（如车票、订单卡片，delta 为完整 JSON）
     */
    COMPONENT
}
