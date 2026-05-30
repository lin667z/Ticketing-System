package org.ticketing_system.biz.aiservice.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.client.LlmClient;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用于将流式 LLM 响应收集并组装成完整文本响应的工具类
 */
@Component
@RequiredArgsConstructor
public class AgentLlmService {

    private final LlmClient llmClient;

    /**
     * 发送 LLM 请求并等待其流式响应结束，返回完整的 AgentLlmResponse
     *
     * @param request LLM 请求对象
     * @return 包含完整内容、模型名称和使用统计的响应对象
     */
    public Mono<AgentLlmResponse> complete(LlmRequest request) {
        return complete(request, null, null, null, null, false);
    }

    public Mono<AgentLlmResponse> complete(
            LlmRequest request,
            AgentTraceEmitter traceEmitter,
            String traceStage,
            AgentType agentType,
            String traceLabel,
            boolean streamToUser) {
        StringBuilder answer = new StringBuilder();
        AtomicReference<String> modelNameRef = new AtomicReference<>();
        AtomicReference<Map<String, Object>> usageRef = new AtomicReference<>(Map.of());
        return llmClient.streamChat(request)
                .doOnNext(response -> {
                    if (response.getType() == LlmStreamResponseType.RETRYING && traceEmitter != null) {
                        traceEmitter.emitRetrying(response.getError());
                        return;
                    }
                    if (response.getModelName() != null) {
                        modelNameRef.set(response.getModelName());
                    }
                    if (response.getUsage() != null && !response.getUsage().isEmpty()) {
                        usageRef.set(response.getUsage());
                    }
                    if (response.getType() == LlmStreamResponseType.CONTENT && response.getDelta() != null) {
                        answer.append(response.getDelta());
                    }
                    if (response.getType() == LlmStreamResponseType.CONTENT && traceEmitter != null) {
                        traceEmitter.emitTrace(traceStage, "CONTENT", agentType, traceLabel, response.getDelta(), null);
                        traceEmitter.emitTrace(traceStage, "REASONING", agentType, traceLabel, null, response.getReasoningDelta());
                        if (streamToUser) {
                            traceEmitter.emitChatChunk(response.getDelta(), response.getReasoningDelta(), response.getModelName());
                        }
                    }
                })
                .then(Mono.fromSupplier(() -> AgentLlmResponse.builder()
                        .content(answer.toString())
                        .modelName(modelNameRef.get())
                        .usage(usageRef.get())
                        .build()));
    }
}
