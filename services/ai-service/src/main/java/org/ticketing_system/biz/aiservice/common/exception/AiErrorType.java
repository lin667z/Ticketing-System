package org.ticketing_system.biz.aiservice.common.exception;

/**
 * AI 服务运行时错误类型，每个类型自带面向用户的友好文案。
 *
 * <p>取代原先在编排器里靠异常 message 的中英文 {@code contains} 匹配来推断错误种类的脆弱做法：
 * 底层按类型抛出 {@link AiServiceException}，编排器按 {@link #getUserMessage()} 直接取文案。</p>
 */
public enum AiErrorType {

    /** LLM 流式响应超时（含渠道无响应、心跳间隔超时） */
    LLM_TIMEOUT("AI 服务响应超时，请稍后再试。"),

    /** 所有渠道均失败，故障转移耗尽 */
    FAILOVER_EXHAUSTED("AI 服务暂时不可用，请稍后再试。"),

    /** 已输出部分内容后渠道失败，无法再切换 */
    PARTIAL_OUTPUT_FAILED("AI 服务连接中断，请稍后再试。"),

    /** 无可用渠道（配置缺失或全部熔断） */
    CHANNEL_UNAVAILABLE("AI 服务连接失败，请稍后再试。"),

    /** 未归类的其他错误 */
    UNKNOWN("服务暂时不可用，请稍后再试。");

    private final String userMessage;

    AiErrorType(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
