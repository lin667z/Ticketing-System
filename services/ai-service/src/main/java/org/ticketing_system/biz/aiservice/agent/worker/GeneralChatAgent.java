package org.ticketing_system.biz.aiservice.agent.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.AgentLlmService;
import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
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
            你是铁路票务系统的 AI 助手请用简洁、自然的中文回答用户
            如果用户需要查票、票价或订单，但缺少必要条件，请直接追问缺失信息，不要编造数据
            """;

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
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(GENERAL_SYSTEM_PROMPT)
                .userMessage(task.getOriginalMessage())
                .messages(baseRequest == null ? null : baseRequest.getMessages())
                .userId(baseRequest == null ? null : baseRequest.getUserId())
                .sessionId(baseRequest == null ? null : baseRequest.getSessionId())
                .maxTokens(getGeneralMaxTokens())
                .build();
        return agentLlmService.complete(request, traceEmitter, getAgentType().name(), getAgentType(), "General Chat Agent", streamToUser)
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

    /**
     * 获取通用聊天允许的最大 Token 数
     */
    private int getGeneralMaxTokens() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? 900 : agent.getGeneralMaxTokens();
    }
}
