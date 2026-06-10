package org.ticketing_system.biz.aiservice.agent.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.core.AgentLlmService;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.agent.core.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.common.enums.ContentStyle;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import reactor.core.publisher.Mono;

/**
 * 通用聊天 Agent，负责处理非业务相关的咨询或闲聊
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralChatAgent implements AiAgent {

    private static final String GENERAL_SYSTEM_PROMPT = """
            你的名字是"铁宝"，你是铁路票务系统的 AI 助手，请用简洁、自然的中文回答用户。
            当用户询问"你是谁"、"你是什么模型"、"你叫什么名字"、"你的身份是什么"等身份相关问题时，
            直接回答"我是铁宝，铁路票务系统的 AI 助手，可以帮你查票、订票、查订单，有什么需要吗？"，
            不要回答任何大模型相关的信息（如 qwen、gpt、deepseek 等），你的身份只有"铁宝"。
            如果用户需要查票、票价或订单，但缺少必要条件，请直接追问缺失信息，不要编造数据。
            回答时注意：
            - 开场欢迎语应热情友好，介绍你能提供的服务
            - 追问缺失信息时，明确列出需要用户补充的内容
            - 操作建议应简洁可行，用肯定的语气给出下一步指引
            """;

    private static final String[] GREETING_KEYWORDS = {
            "你好", "您好", "hi", "hello", "嗨", "在吗", "在不在",
            "你是谁", "你能做什么", "有什么功能", "介绍一下", "帮助", "help"
    };

    private final AgentLlmService agentLlmService;
    private final AiProperties aiProperties;

    @Override
    public AgentType getAgentType() {
        return AgentType.GENERAL_CHAT;
    }

    /**
     * 执行通用聊天任务
     *
     * @param task 代理任务
     * @return 包含 LLM 生成回复的任务结果
     */
    @Override
    public Mono<AgentTaskResult> execute(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser) {
        LlmRequest baseRequest = task.getLlmRequest();
        ContentStyle contentStyle = detectContentStyle(task.getOriginalMessage());
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(GENERAL_SYSTEM_PROMPT)
                .userMessage(task.getOriginalMessage())
                .messages(baseRequest == null ? null : baseRequest.getMessages())
                .userId(baseRequest == null ? null : baseRequest.getUserId())
                .sessionId(baseRequest == null ? null : baseRequest.getSessionId())
                .maxTokens(getGeneralMaxTokens())
                .build();
        return agentLlmService.complete(request, traceEmitter, getAgentType().name(), getAgentType(), "General Chat Agent", streamToUser, contentStyle)
                .map(response -> AgentTaskResult.builder()
                        .type(getAgentType())
                        .success(true)
                        .summary(response.getContent())
                        .streamedToUser(streamToUser)
                        .build())
                .onErrorResume(ex -> {
                    log.warn("通用聊天 Agent 执行失败: {}", ex.getMessage(), ex);
                    return Mono.just(AgentTaskResult.failure(getAgentType(), "暂时无法生成回答，请稍后再试"));
                });
    }

    private ContentStyle detectContentStyle(String message) {
        if (message == null || message.isBlank()) {
            return ContentStyle.NORMAL;
        }
        String lowerMsg = message.toLowerCase().trim();
        for (String keyword : GREETING_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                return ContentStyle.GREETING;
            }
        }
        return ContentStyle.NORMAL;
    }

    /**
     * 获取通用聊天允许的最大 Token 数
     */
    private int getGeneralMaxTokens() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? 900 : agent.getGeneralMaxTokens();
    }
}
