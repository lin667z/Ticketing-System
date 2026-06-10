package org.ticketing_system.biz.aiservice.common.exception;

import lombok.Getter;

/**
 * AI 服务类型化运行时异常。
 *
 * <p>携带 {@link AiErrorType}，使上层无需匹配 message 字符串即可映射用户文案。
 * 仅用于编排链路内部的错误传播与降级，不经全局异常处理器（流式响应将其转为错误事件块）。</p>
 */
@Getter
public class AiServiceException extends RuntimeException {

    private final AiErrorType type;

    public AiServiceException(AiErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public AiServiceException(AiErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    /**
     * 从任意异常解析出 AI 错误类型：
     * 沿 cause 链查找首个 {@link AiServiceException} 取其类型；
     * 否则识别超时异常为 {@link AiErrorType#LLM_TIMEOUT}；其余归为 {@link AiErrorType#UNKNOWN}。
     */
    public static AiErrorType resolveType(Throwable ex) {
        Throwable current = ex;
        int guard = 0;
        while (current != null && guard++ < 16) {
            if (current instanceof AiServiceException aiEx) {
                return aiEx.getType();
            }
            if (current instanceof java.util.concurrent.TimeoutException) {
                return AiErrorType.LLM_TIMEOUT;
            }
            current = current.getCause();
        }
        return AiErrorType.UNKNOWN;
    }
}
