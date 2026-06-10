package org.ticketing_system.biz.aiservice.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 完整的非流式 LLM 响应，由流式块组装而成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLlmResponse {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型使用情况统计
     */
    @Builder.Default
    private Map<String, Object> usage = Map.of();
}
