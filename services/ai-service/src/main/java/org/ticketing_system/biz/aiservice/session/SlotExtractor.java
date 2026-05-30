package org.ticketing_system.biz.aiservice.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.AgentLlmService;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SlotExtractionResult;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts slot patches from user input and compact history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotExtractor {

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final String SLOT_EXTRACT_SYSTEM_PROMPT = """
            你是铁路票务 Slot Extractor，只抽取结构化槽位变化，不做路由，不回答用户。
            今天日期：%s。
            只输出 JSON，不要 Markdown，不要解释。
            允许字段：
            - slotPatch.departure: 出发地
            - slotPatch.arrival: 到达地
            - slotPatch.date: 出行日期，必须 yyyy-MM-dd；无法唯一确定则不要输出该字段
            - slotPatch.trainNumber: 车次
            - slotPatch.orderDate: 订单查询日期，必须 yyyy-MM-dd
            - slotPatch.orderCount: 订单返回条数
            - clearSlots: 只能包含 ticket、order、all
            订单查询不要写入 departure、arrival、trainNumber。
            不确定的字段不要猜测，留空或省略。
            输出 schema:
            {"slotPatch":{},"clearSlots":[],"confidence":{},"intentHint":""}
            """;

    private final AgentLlmService agentLlmService;

    public Mono<SlotExtractionResult> extract(String userMessage,
                                              List<LlmRequest.Message> recentTurns,
                                              SessionSlotState currentSlot,
                                              Long userId,
                                              Long sessionId) {
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(String.format(SLOT_EXTRACT_SYSTEM_PROMPT, LocalDate.now()))
                .messages(recentTurns)
                .userMessage(buildCurrentPrompt(userMessage, currentSlot))
                .userId(userId)
                .sessionId(sessionId)
                .maxTokens(512)
                .build();
        return agentLlmService.complete(request)
                .map(response -> parse(response.getContent()))
                .onErrorResume(ex -> {
                    log.warn("Slot extraction failed: sessionId={}, error={}", sessionId, ex.getMessage(), ex);
                    return Mono.just(SlotExtractionResult.empty());
                });
    }

    public Mono<SlotExtractionResult> rebuildFromHistory(List<LlmRequest.Message> history,
                                                         Long userId,
                                                         Long sessionId) {
        if (history == null || history.isEmpty()) {
            return Mono.just(SlotExtractionResult.empty());
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(String.format(SLOT_EXTRACT_SYSTEM_PROMPT, LocalDate.now()))
                .messages(history)
                .userMessage("请根据以上完整历史重建当前仍有效的 Slot 状态。")
                .userId(userId)
                .sessionId(sessionId)
                .maxTokens(512)
                .build();
        return agentLlmService.complete(request)
                .map(response -> parse(response.getContent()))
                .onErrorResume(ex -> {
                    log.warn("Slot rebuild failed: sessionId={}, error={}", sessionId, ex.getMessage(), ex);
                    return Mono.just(SlotExtractionResult.empty());
                });
    }

    private String buildCurrentPrompt(String userMessage, SessionSlotState currentSlot) {
        return "历史 Slot 状态：\n" + JSON.toJSONString(currentSlot == null ? SessionSlotState.empty() : currentSlot)
                + "\n\n当前用户输入：\n" + (userMessage == null ? "" : userMessage);
    }

    private SlotExtractionResult parse(String content) {
        if (content == null || content.isBlank()) {
            return SlotExtractionResult.empty();
        }
        try {
            Matcher matcher = JSON_OBJECT_PATTERN.matcher(content);
            String json = matcher.find() ? matcher.group() : content;
            JSONObject object = JSON.parseObject(json);
            JSONObject slotPatchObject = object.getJSONObject("slotPatch");
            JSONObject confidenceObject = object.getJSONObject("confidence");
            return SlotExtractionResult.builder()
                    .slotPatch(slotPatchObject == null ? Map.of() : slotPatchObject)
                    .clearSlots(object.getList("clearSlots", String.class) == null ? List.of() : object.getList("clearSlots", String.class))
                    .confidence(parseConfidence(confidenceObject))
                    .intentHint(object.getString("intentHint"))
                    .build();
        } catch (Exception ex) {
            log.warn("Slot extraction parse failed: content={}", content, ex);
            return SlotExtractionResult.empty();
        }
    }

    private Map<String, Double> parseConfidence(JSONObject confidenceObject) {
        if (confidenceObject == null || confidenceObject.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> confidence = new HashMap<>();
        for (Map.Entry<String, Object> entry : confidenceObject.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number) {
                confidence.put(entry.getKey(), number.doubleValue());
            }
        }
        return confidence;
    }
}
