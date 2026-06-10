package org.ticketing_system.biz.aiservice.agent.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.model.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.common.enums.ContentStyle;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dto.domain.AiStreamChunk;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将各子 Agent 的输出聚合为最终面向用户的响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentResponseAggregator {

    private static final String AGGREGATOR_SYSTEM_PROMPT = """
            你的名字是"铁宝"，你是铁路票务 AI 的 Aggregator，请根据子 Agent 的真实结果生成简洁自然的中文回答。
            当回答中包含自我介绍时，称自己为"铁宝"，不要提及任何大模型名称。
            不要编造没有出现在子 Agent 结果中的车次、价格、订单状态或时间。
            如果部分任务失败，请说明失败项并保留成功项。
            如果结果包含此前已完成但延迟返回的任务，也请自然整合为一次完整回复，不要暴露内部缓冲流程。
            """;

    private final AgentLlmService agentLlmService;
    private final AiProperties aiProperties;

    /**
     * 聚合子任务结果
     *
     * @param plan        代理计划
     * @param results     任务执行结果列表
     * @param baseRequest 原始 LLM 请求
     * @return 聚合后的执行结果
     */
    public Flux<AiStreamChunk> aggregateAsFlux(AgentPlan plan, List<AgentTaskResult> results, LlmRequest baseRequest, AgentTraceEmitter traceEmitter) {
        if (traceEmitter != null) {
            traceEmitter.emitStage("正在整理结果", "正在整理 Agent 执行结果");
        }
        if (results == null || results.isEmpty()) {
            return Flux.empty();
        }
        if (!shouldUseLlmAggregation(plan, results)) {
            if (hasStreamedAnswer(results)) {
                return Flux.empty();
            }
            if (traceEmitter != null) {
                traceEmitter.emitStage("正在整理结果", "已生成业务查询摘要，开始流式输出");
            }
            String summary = joinSummaries(results);
            ContentStyle style = resolveAggregationStyle(results);
            return Flux.just(AiStreamChunk.chatChunk(summary, style));
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(AGGREGATOR_SYSTEM_PROMPT)
                .userMessage(buildAggregationPrompt(baseRequest.getUserMessage(), results))
                .userId(baseRequest.getUserId())
                .sessionId(baseRequest.getSessionId())
                .maxTokens(getAggregatorMaxTokens())
                .build();
        ContentStyle style = resolveAggregationStyle(results);
        return agentLlmService.streamToUser(request, traceEmitter, "AGGREGATOR", null, "Aggregator Agent")
                .filter(response -> response.getType() == LlmStreamResponseType.CONTENT && response.getDelta() != null)
                .map(response -> {
                    AiStreamChunk chunk = AiStreamChunk.chatChunk(response.getDelta(), style);
                    chunk.setModelName(response.getModelName());
                    return chunk;
                })
                .onErrorResume(ex -> {
                    log.warn("Agent 结果聚合失败，回退到直接合并总结内容: {}", ex.getMessage(), ex);
                    return Flux.just(AiStreamChunk.chatChunk(joinSummaries(results), style));
                });
    }

    /**
     * 判断是否应使用 LLM 进行聚合
     */
    private boolean shouldUseLlmAggregation(AgentPlan plan, List<AgentTaskResult> results) {
        AiProperties.Agent agent = aiProperties.getAgent();
        boolean enabled = agent == null || agent.isAggregatorLlmEnabled();
        return enabled && (results.size() > 1 || (plan != null && plan.isNeedAggregation()));
    }

    private boolean hasStreamedAnswer(List<AgentTaskResult> results) {
        return results != null && results.stream().anyMatch(AgentTaskResult::isStreamedToUser);
    }

    /**
     * 构建聚合提示词
     */
    private String buildAggregationPrompt(String userMessage, List<AgentTaskResult> results) {
        String joinedResults = results.stream()
                .map(result -> result.getType() + ": " + result.getSummary())
                .collect(Collectors.joining("\n"));
        return "用户原始问题：\n" + userMessage + "\n\n子 Agent 结果：\n" + joinedResults;
    }

    /**
     * 直接拼接所有任务的总结内容
     */
    private String joinSummaries(List<AgentTaskResult> results) {
        return results.stream()
                .map(AgentTaskResult::getSummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 获取聚合器的最大 Token 数
     */
    private int getAggregatorMaxTokens() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? 700 : agent.getAggregatorMaxTokens();
    }

    /**
     * 解析聚合输出的内容渲染风格：
     * 存在失败任务 → ERROR（前端高亮失败提示）；
     * 含结构化组件（车次/订单卡片等）→ SUMMARY（与组件搭配的摘要文案）；
     * 其余纯文本回答 → NORMAL。
     */
    private ContentStyle resolveAggregationStyle(List<AgentTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return ContentStyle.NORMAL;
        }
        boolean hasFailure = results.stream().anyMatch(r -> !r.isSuccess());
        if (hasFailure) {
            return ContentStyle.ERROR;
        }
        boolean hasStructured = results.stream().anyMatch(r -> r.getComponentType() != null);
        if (hasStructured) {
            return ContentStyle.SUMMARY;
        }
        return ContentStyle.NORMAL;
    }
}
