package org.ticketing_system.biz.aiservice.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.AgentLlmService;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts compressed non-executable facts from the conversation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextExtractor {

    private static final int MAX_FACTS = 8;
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是铁路票务 Session Context Extractor，只提取非结构化辅助理解信息。
            只输出 JSON，不要 Markdown，不要解释。
            输出固定 schema: {"facts":[]}
            facts 只保存用户偏好、约束、表达习惯，例如偏好高铁、不坐夜车、上午出发、少换乘、预算敏感。
            禁止保存 Slot 中已有或应属于 Slot 的字段：出发地、到达地、出行日期、车次、订单查询日期、订单数量。
            没有有效信息时必须返回 {"facts":[]}。
            """;

    private final AgentLlmService agentLlmService;

    public Mono<SessionSummaryContext> extract(List<LlmRequest.Message> messages,
                                               SessionSummaryContext currentSummary,
                                               SessionSlotState slotState,
                                               Long userId,
                                               Long sessionId) {
        if (messages == null || messages.isEmpty()) {
            return Mono.just(normalize(currentSummary, slotState));
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(SUMMARY_SYSTEM_PROMPT)
                .messages(messages)
                .userMessage("请基于以上对话和已有 facts 去重提取辅助理解信息。已有 facts："
                        + JSON.toJSONString(currentSummary == null ? SessionSummaryContext.empty() : currentSummary))
                .userId(userId)
                .sessionId(sessionId)
                .maxTokens(512)
                .build();
        return agentLlmService.complete(request)
                .map(response -> parse(response.getContent()))
                .map(summary -> normalize(summary, slotState))
                .onErrorResume(ex -> {
                    log.warn("Session context extraction failed: sessionId={}, error={}", sessionId, ex.getMessage(), ex);
                    return Mono.just(normalize(currentSummary, slotState));
                });
    }

    public SessionSummaryContext normalize(SessionSummaryContext summary, SessionSlotState slotState) {
        if (summary == null || summary.getFacts() == null || summary.getFacts().isEmpty()) {
            return SessionSummaryContext.empty();
        }
        Set<String> facts = new LinkedHashSet<>();
        for (String fact : summary.getFacts()) {
            if (fact == null || fact.isBlank() || containsSlotValue(fact, slotState)) {
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
            Matcher matcher = JSON_OBJECT_PATTERN.matcher(content);
            String json = matcher.find() ? matcher.group() : content;
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

    private boolean containsSlotValue(String fact, SessionSlotState slotState) {
        if (slotState == null) {
            return false;
        }
        SessionSlotState.TicketSlot ticket = slotState.getTicket();
        SessionSlotState.OrderQuerySlot order = slotState.getOrderQuery();
        return contains(fact, ticket == null ? null : ticket.getDeparture())
                || contains(fact, ticket == null ? null : ticket.getArrival())
                || contains(fact, ticket == null ? null : ticket.getDate())
                || contains(fact, ticket == null ? null : ticket.getTrainNumber())
                || contains(fact, order == null ? null : order.getDate())
                || contains(fact, order == null || order.getCount() == null ? null : String.valueOf(order.getCount()));
    }

    private boolean contains(String source, String value) {
        return value != null && !value.isBlank() && source.contains(value);
    }
}
