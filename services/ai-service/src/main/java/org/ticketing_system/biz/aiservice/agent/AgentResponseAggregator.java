package org.ticketing_system.biz.aiservice.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将各子 Agent 的输出聚合为最终面向用户的响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentResponseAggregator {

    private static final String AGGREGATOR_SYSTEM_PROMPT = """
            你是铁路票务 AI 的 Aggregator请根据子 Agent 的真实结果生成简洁自然的中文回答
            不要编造没有出现在子 Agent 结果中的车次、价格、订单状态或时间
            如果部分任务失败，请说明失败项并保留成功项
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
    public Mono<AgentExecutionResult> aggregate(AgentPlan plan, List<AgentTaskResult> results, LlmRequest baseRequest, AgentTraceEmitter traceEmitter) {
        if (traceEmitter != null) {
            traceEmitter.emitStatus("AGGREGATOR", "结果整理", "正在整理 Agent 执行结果");
        }
        if (results == null || results.isEmpty()) {
            return Mono.just(AgentExecutionResult.builder()
                    .answer("我还需要更多信息才能处理这个请求")
                    .taskResults(List.of())
                    .build());
        }
        if (!shouldUseLlmAggregation(plan, results)) {
            if (traceEmitter != null) {
                traceEmitter.emitStatus("AGGREGATOR", "结果整理", "已生成业务查询摘要，开始流式输出");
            }
            return Mono.just(AgentExecutionResult.builder()
                    .answer(joinSummaries(results))
                    .taskResults(results)
                    .answerStreamed(hasStreamedAnswer(results))
                    .build());
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(AGGREGATOR_SYSTEM_PROMPT)
                .userMessage(buildAggregationPrompt(baseRequest.getUserMessage(), results))
                .userId(baseRequest.getUserId())
                .sessionId(baseRequest.getSessionId())
                .maxTokens(getAggregatorMaxTokens())
                .build();
        return agentLlmService.complete(request, traceEmitter, "AGGREGATOR", null, "Aggregator Agent", true)
                .map(response -> AgentExecutionResult.builder()
                        .answer(response.getContent())
                        .taskResults(results)
                        .usage(response.getUsage())
                        .modelName(response.getModelName())
                        .answerStreamed(true)
                        .build())
                .onErrorResume(ex -> {
                    log.warn("Agent 结果聚合失败，回退到直接合并总结内容: {}", ex.getMessage(), ex);
                    return Mono.just(AgentExecutionResult.builder()
                            .answer(joinSummaries(results))
                            .taskResults(results)
                            .build());
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
}
