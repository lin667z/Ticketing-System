package org.ticketing_system.biz.aiservice.client.impl;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.completions.CompletionUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.client.LlmClient;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.client.dto.LlmStreamResponse;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.strategy.FailoverRouteManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 客户端实现，基于 OpenAI 兼容 SDK 提供流式调用
 */
@Slf4j
@Component
public class SpringAiLlmClientImpl implements LlmClient {

    private static final String REASONING_CONTENT_KEY = "reasoning_content";

    private final FailoverRouteManager failoverRouteManager;

    public SpringAiLlmClientImpl(FailoverRouteManager failoverRouteManager) {
        this.failoverRouteManager = failoverRouteManager;
    }

    @Override
    public Flux<LlmStreamResponse> streamChat(LlmRequest request) {
        // 委托给路由管理器执行带故障转移的调用
        return failoverRouteManager.executeWithFailover(request, routeNode -> executeStream(routeNode, request));
    }

    /**
     * 针对单个路由节点执行流式调用，使用同步客户端 + 调度器避免 OkHttp 缓冲
     */
    private Flux<LlmStreamResponse> executeStream(AiProperties.RouteNode routeNode,
            LlmRequest request) {
        OpenAIClient client = failoverRouteManager.getOrCreateSyncClient(routeNode.getProvider());
        ChatCompletionCreateParams params = buildParams(request, routeNode.getRealModel());

        return Flux.<LlmStreamResponse>create(sink -> {
            AtomicReference<StreamResponse<ChatCompletionChunk>> streamRef = new AtomicReference<>();
            sink.onDispose(() -> closeSyncStream(streamRef.get()));

            try {
                StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params);
                streamRef.set(stream);
                stream.stream().forEach(chunk -> {
                    if (sink.isCancelled()) {
                        closeSyncStream(stream);
                        return;
                    }
                    handleChunk(chunk, routeNode.getRealModel(), sink);
                });
                if (!sink.isCancelled()) {
                    sink.complete();
                }
            } catch (Exception ex) {
                if (!sink.isCancelled()) {
                    sink.error(ex);
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Mono.delay(Duration.ofMillis(routeNode.getTimeoutMs())))
                .doOnSubscribe(subscription -> log.info(
                        "AI stream route started: routeId={}, provider={}, model={}",
                        routeNode.getId(), routeNode.getProvider(), routeNode.getRealModel()));
    }

    /**
     * 将 OpenAI SDK 的 chunk 转换为统一的 LlmStreamResponse
     */
    private void handleChunk(ChatCompletionChunk chunk,
            String modelName,
            reactor.core.publisher.FluxSink<LlmStreamResponse> sink) {
        Map<String, Object> usage = toUsageMap(chunk.usage().orElse(null));
        String finishReason = chunk.choices().stream()
                .findFirst()
                .flatMap(ChatCompletionChunk.Choice::finishReason)
                .map(ChatCompletionChunk.Choice.FinishReason::asString)
                .orElse(null);

        // 先发送内容增量，确保最后一个 chunk 的内容不丢失
        chunk.choices().stream()
                .findFirst()
                .map(ChatCompletionChunk.Choice::delta)
                .ifPresent(delta -> {
                    String content = delta.content().orElse("");
                    String reasoning = extractReasoning(delta);
                    if (content.isEmpty() && reasoning.isEmpty()) {
                        return;
                    }
                    sink.next(LlmStreamResponse.builder()
                            .type(LlmStreamResponseType.CONTENT)
                            .delta(content)
                            .reasoningDelta(reasoning)
                            .modelName(modelName)
                            .finishReason(finishReason)
                            .usage(usage)
                            .metadata(Map.of())
                            .build());
                });

        // 仅当存在结束原因时发送 FINISH 信号
        if (finishReason != null) {
            sink.next(LlmStreamResponse.finish(modelName, finishReason, usage));
        }

    }

    /**
     * 构建 OpenAI API 请求参数，包含系统提示词、消息历史和用户输入
     */
    private ChatCompletionCreateParams buildParams(LlmRequest request, String model) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());

        // 系统提示词
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            builder.addSystemMessage(request.getSystemPrompt());
        }

        // 历史消息
        if (request.getMessages() != null) {
            for (LlmRequest.Message message : request.getMessages()) {
                if (message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }
                String role = message.getRole();
                String content = message.getContent();
                if ("assistant".equals(role)) {
                    builder.addAssistantMessage(content);
                } else if ("system".equals(role)) {
                    builder.addSystemMessage(content);
                } else if ("tool".equals(role)) {
                    // tool 角色消息：需要关联 tool_call_id
                    // 当前简化实现：将 tool 结果作为 user 消息注入
                    builder.addUserMessage("[工具返回] " + content);
                } else {
                    builder.addUserMessage(content);
                }
            }
        }

        // 当前用户消息
        String userMessage = request.getUserMessage();
        builder.addUserMessage(userMessage == null ? "" : userMessage);

        // 温度参数
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        // 最大 Token
        if (request.getMaxTokens() != null) {
            builder.maxCompletionTokens(request.getMaxTokens());
        }

        return builder.build();
    }

    /**
     * 从 delta 中提取 reasoning_content（仅适用于支持推理过程的模型）
     */
    private String extractReasoning(ChatCompletionChunk.Choice.Delta delta) {
        JsonValue reasoningValue = delta._additionalProperties().get(REASONING_CONTENT_KEY);
        if (reasoningValue == null) {
            return "";
        }
        try {
            return reasoningValue.accept(new JsonValue.Visitor<>() {
                @Override
                public String visitString(String value) {
                    return value;
                }

                @Override
                public String visitDefault() {
                    return "";
                }
            });
        } catch (Exception ex) {
            log.debug("Failed to extract reasoning_content from stream chunk", ex);
            return "";
        }
    }

    /**
     * 将 CompletionUsage 转换为 Map
     */
    private Map<String, Object> toUsageMap(CompletionUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("promptTokens", usage.promptTokens());
        values.put("completionTokens", usage.completionTokens());
        values.put("totalTokens", usage.totalTokens());
        return values;
    }

    /**
     * 安静关闭同步流，忽略关闭时的异常
     */
    private void closeSyncStream(StreamResponse<ChatCompletionChunk> stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception ex) {
            log.debug("Failed to close OpenAI stream", ex);
        }
    }
}
