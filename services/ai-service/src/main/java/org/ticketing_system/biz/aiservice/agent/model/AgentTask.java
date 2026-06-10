package org.ticketing_system.biz.aiservice.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventCollector;
import org.ticketing_system.biz.aiservice.memory.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个可执行的子 Agent 任务
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    /**
     * 执行任务的 Agent 类型
     */
    private AgentType type;

    /**
     * 任务意图
     */
    private String intent;

    /**
     * 任务参数列表
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * 当前任务缺失的必要字段，用于跨轮补参恢复
     */
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    /**
     * 挂起任务恢复标识，同一会话内保持稳定
     */
    private String resumeKey;

    /**
     * 当前任务是否依赖澄清补参
     */
    private boolean dependsOnClarification;

    /**
     * 同一轮多意图任务分组标识
     */
    private String taskGroup;

    /**
     * 用户的原始输入消息
     */
    private String originalMessage;

    /**
     * 聊天请求上下文信息
     */
    private AiChatRequestContext context;

    /**
     * LLM 请求对象
     */
    private LlmRequest llmRequest;

    private ExecutionContext executionContext;

    private SessionEventCollector sessionEventCollector;

    /**
     * 获取字符串类型的参数值
     *
     * @param key 参数键
     * @return 过滤空白字符后的参数值，若不存在或为空则返回 null
     */
    public String getStringParam(String key) {
        Object value = parameters == null ? null : parameters.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public boolean hasMissingFields() {
        return missingFields != null && !missingFields.isEmpty();
    }
}
