package org.ticketing_system.biz.aiservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;

import java.util.Map;

/**
 * LLM 流式响应 DTO，封装大模型返回的文本分片、工具调用、Token 使用量等信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmStreamResponse {

    /** 响应类型 (内容、工具开始、工具结束、完成) */
    private LlmStreamResponseType type;

    /** 文本分片内容 */
    private String delta;

    /** 思维链/推理分片内容 */
    private String reasoningDelta;

    /** 模型名称 */
    private String modelName;

    /** 错误信息 */
    private String error;

    /** 模型返回的结束原因 */
    private String finishReason;

    /** 模型返回的 Token 使用量 */
    private Map<String, Object> usage;

    /** 响应元数据 */
    private Map<String, Object> metadata;

    /**
     * 构建结束状态的响应
     */
    public static LlmStreamResponse finish(String modelName, String finishReason, Map<String, Object> usage) {
        return LlmStreamResponse.builder()
                .type(LlmStreamResponseType.FINISH)
                .modelName(modelName)
                .finishReason(finishReason)
                .usage(usage)
                .build();
    }
}
