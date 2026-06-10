package org.ticketing_system.biz.aiservice.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.core.AgentLlmService;
import org.ticketing_system.biz.aiservice.common.util.JsonExtractor;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从对话中提取压缩后的非可执行事实信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextExtractor {

    private static final int MAX_FACTS = 8;
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是铁路票务 Session Context Extractor，只提取非结构化辅助理解信息。
            只输出 JSON，不要 Markdown，不要解释。
            输出固定 schema: {"facts":[]}
            facts 只保存历史对话轮次中提供的重要、有效信息，可以为后续对话提供上下文参考。
            禁止保存冗余信息或空泛内容。
            没有有效信息时必须返回 {"facts":[]}。
            """;

    private final AgentLlmService agentLlmService;

    public Mono<SessionSummaryContext> extract(List<LlmRequest.Message> messages,
                                               SessionSummaryContext currentSummary,
                                               SessionSlotState slotState,
                                               Long userId,
                                               Long sessionId) {
        if (messages == null || messages.isEmpty()) {
            return Mono.just(normalize(currentSummary));
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(SUMMARY_SYSTEM_PROMPT)
                .messages(messages)
                .userMessage("请基于以上对话和已有 facts 去重提取重要有效信息。已有 facts："
                        + JSON.toJSONString(currentSummary == null ? SessionSummaryContext.empty() : currentSummary))
                .userId(userId)
                .sessionId(sessionId)
                .maxTokens(512)
                .build();
        return agentLlmService.complete(request)
                .map(response -> parse(response.getContent()))
                .map(summary -> normalize(summary))
                .onErrorResume(ex -> {
                    log.warn("Session context extraction failed: sessionId={}, error={}", sessionId, ex.getMessage(), ex);
                    return Mono.just(normalize(currentSummary));
                });
    }

    public SessionSummaryContext normalize(SessionSummaryContext summary) {
        if (summary == null || summary.getFacts() == null || summary.getFacts().isEmpty()) {
            return SessionSummaryContext.empty();
        }
        Set<String> facts = new LinkedHashSet<>();
        for (String fact : summary.getFacts()) {
            if (fact == null || fact.isBlank()) {
                continue;
            }
            facts.add(fact.trim());
            if (facts.size() >= MAX_FACTS) {
                break;
            }
        }
        return SessionSummaryContext.builder()
                .facts(new ArrayList<>(facts))
                .build();
    }

    private SessionSummaryContext parse(String content) {
        if (content == null || content.isBlank()) {
            return SessionSummaryContext.empty();
        }
        try {
            String json = JsonExtractor.firstJsonObject(content);
            JSONObject object = JSON.parseObject(json);
            List<String> facts = object.getList("facts", String.class);
            return SessionSummaryContext.builder()
                    .facts(facts == null ? List.of() : facts)
                    .build();
        } catch (Exception ex) {
            log.warn("Session context parse failed: content={}", content, ex);
            return SessionSummaryContext.empty();
        }
    }

}
