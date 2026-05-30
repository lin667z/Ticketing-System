package org.ticketing_system.biz.aiservice.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;

import java.util.HashMap;
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
}
