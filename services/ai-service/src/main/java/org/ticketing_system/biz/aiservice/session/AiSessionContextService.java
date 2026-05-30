package org.ticketing_system.biz.aiservice.session;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.util.AiBusinessContextSignals;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.memory.ChatMemoryService;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import org.ticketing_system.biz.aiservice.session.context.SessionContextMeta;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads, rebuilds, updates and persists the two-track session context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionContextService {

    private static final int DEFAULT_CONTEXT_TTL_SECONDS = 86400;
    private static final int DEFAULT_LAST_TURNS = 2;
    private static final int DEFAULT_SUMMARY_START_TURN = 3;
    private static final String SLOT_KEY_TEMPLATE = "ai:session:%s:%s:slot";
    private static final String SUMMARY_KEY_TEMPLATE = "ai:session:%s:%s:summary";
    private static final String LAST_TURNS_KEY_TEMPLATE = "ai:session:%s:%s:last_turns";
    private static final String META_KEY_TEMPLATE = "ai:session:%s:%s:meta";
    private static final int SLOT_CACHE_INDEX = 0;
    private static final int SUMMARY_CACHE_INDEX = 1;
    private static final int META_CACHE_INDEX = 2;
    private static final int LAST_TURNS_CACHE_INDEX = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatMemoryService chatMemoryService;
    private final SlotExtractor slotExtractor;
    private final SlotStateMerger slotStateMerger;
    private final SessionContextExtractor sessionContextExtractor;
    private final AiProperties aiProperties;

    public Mono<AiSessionContext> prepare(AiChatRequestContext requestContext, AiChatMessage userMessage) {
        Long userId = requestContext.getUserId();
        Long sessionId = requestContext.getSessionId();
        String userMessageContent = userMessage == null ? null : userMessage.getContent();
        CachedContext cached = requestContext.isNewSession() ? newSessionContext() : loadCached(userId, sessionId);
        boolean contextDependent = AiBusinessContextSignals.isContextDependent(userMessageContent);
        boolean needsHistory = !requestContext.isNewSession() && (cached.slotMissing() || cached.summaryMissing() || contextDependent);
        List<LlmRequest.Message> historyMessages = needsHistory ? toMessages(chatMemoryService.getAllMessages(sessionId, userId)) : List.of();
        Mono<CachedContext> baseContextMono = shouldRebuild(cached, historyMessages, contextDependent)
                ? rebuild(userId, sessionId, historyMessages, cached)
                : Mono.just(cached);

        return baseContextMono.flatMap(baseContext -> {
            SessionSlotState currentSlot = baseContext.slotState();
            List<LlmRequest.Message> recentTurns = resolveRecentTurns(baseContext, sessionId);
            SlotExtractionInput slotInput = new SlotExtractionInput(userMessageContent, recentTurns, currentSlot, userId, sessionId, contextDependent);
            return maybeExtractSlot(slotInput)
                    .flatMap(mergedSlot -> maybeExtractSummary(new SummaryExtractionInput(
                            userId,
                            sessionId,
                            baseContext.summaryContext(),
                            mergedSlot,
                            recentTurns,
                            userMessage,
                            baseContext.meta()))
                            .map(summary -> {
                                SessionContextMeta meta = incrementMeta(baseContext.meta());
                                requestContext.setKnownTurnCount(meta.getTurnCount());
                                AiSessionContext sessionContext = AiSessionContext.builder()
                                        .slotState(mergedSlot)
                                        .summaryContext(summary)
                                        .recentTurns(recentTurns)
                                        .turnCount(meta.getTurnCount())
                                        .recovered(baseContext.recovered())
                                        .build();
                                writeContext(userId, sessionId, sessionContext, meta);
                                return sessionContext;
                            }));
        });
    }

    public void persistRecentTurns(AiChatRequestContext requestContext, String userMessage, String assistantAnswer) {
        AiSessionContext sessionContext = requestContext.getSessionContext();
        if (sessionContext == null) {
            return;
        }
        List<LlmRequest.Message> updated = new ArrayList<>(sessionContext.getRecentTurns() == null ? List.of() : sessionContext.getRecentTurns());
        if (userMessage != null && !userMessage.isBlank()) {
            updated.add(LlmRequest.Message.builder().role("user").content(userMessage).build());
        }
        if (assistantAnswer != null && !assistantAnswer.isBlank()) {
            updated.add(LlmRequest.Message.builder().role("assistant").content(assistantAnswer).build());
        }
        int maxMessages = getLastTurns() * 2;
        if (updated.size() > maxMessages) {
            updated = updated.subList(updated.size() - maxMessages, updated.size());
        }
        sessionContext.setRecentTurns(updated);
        set(lastTurnsKey(requestContext.getUserId(), requestContext.getSessionId()), JSON.toJSONString(updated));
    }

    private CachedContext newSessionContext() {
        return new CachedContext(
                SessionSlotState.empty(),
                SessionSummaryContext.empty(),
                List.of(),
                SessionContextMeta.builder().build(),
                false,
                false,
                false,
                false);
    }

    private Mono<CachedContext> rebuild(Long userId, Long sessionId, List<LlmRequest.Message> historyMessages, CachedContext cached) {
        return slotExtractor.rebuildFromHistory(historyMessages, userId, sessionId)
                .map(extraction -> slotStateMerger.merge(SessionSlotState.empty(), extraction))
                .flatMap(slotState -> sessionContextExtractor.extract(historyMessages, cached.summaryContext(), slotState, userId, sessionId)
                        .map(summary -> new CachedContext(slotState, summary, recentFrom(historyMessages), cached.meta(), true, false, false, false)));
    }

    private Mono<SessionSlotState> maybeExtractSlot(SlotExtractionInput input) {
        SessionSlotState safeSlot = input.currentSlot() == null ? SessionSlotState.empty() : input.currentSlot();
        if (!shouldExtractSlot(input.userMessage(), input.recentTurns(), safeSlot, input.contextDependent())) {
            return Mono.just(safeSlot);
        }
        return slotExtractor.extract(input.userMessage(), input.recentTurns(), safeSlot, input.userId(), input.sessionId())
                .map(extraction -> slotStateMerger.merge(safeSlot, extraction));
    }

    private Mono<SessionSummaryContext> maybeExtractSummary(SummaryExtractionInput input) {
        int nextTurn = incrementMeta(input.meta()).getTurnCount();
        if (nextTurn <= getSummaryStartTurn()) {
            return Mono.just(sessionContextExtractor.normalize(input.currentSummary(), input.slotState()));
        }
        List<LlmRequest.Message> messages = new ArrayList<>(input.recentTurns() == null ? List.of() : input.recentTurns());
        if (input.userMessage() != null && input.userMessage().getContent() != null) {
            messages.add(LlmRequest.Message.builder()
                    .role("user")
                    .content(input.userMessage().getContent())
                    .build());
        }
        return sessionContextExtractor.extract(messages, input.currentSummary(), input.slotState(), input.userId(), input.sessionId());
    }

    private CachedContext loadCached(Long userId, Long sessionId) {
        List<String> keys = List.of(
                slotKey(userId, sessionId),
                summaryKey(userId, sessionId),
                metaKey(userId, sessionId),
                lastTurnsKey(userId, sessionId));
        List<String> values = multiGet(keys);
        String slotJson = valueAt(values, SLOT_CACHE_INDEX);
        String summaryJson = valueAt(values, SUMMARY_CACHE_INDEX);
        String metaJson = valueAt(values, META_CACHE_INDEX);
        String recentTurnsJson = valueAt(values, LAST_TURNS_CACHE_INDEX);
        ParseResult<SessionSlotState> slotState = parse(slotJson, SessionSlotState.class, SessionSlotState.empty());
        ParseResult<SessionSummaryContext> summary = parse(summaryJson, SessionSummaryContext.class, SessionSummaryContext.empty());
        ParseResult<SessionContextMeta> meta = parse(metaJson, SessionContextMeta.class, SessionContextMeta.builder().build());
        ParseResult<List<LlmRequest.Message>> recentTurns = parseMessageList(recentTurnsJson);
        return new CachedContext(
                slotState.value(),
                summary.value(),
                recentTurns.value(),
                meta.value(),
                false,
                isBlank(slotJson) || slotState.invalid(),
                isBlank(summaryJson) || summary.invalid(),
                isBlank(recentTurnsJson) || recentTurns.invalid());
    }

    private boolean shouldRebuild(CachedContext cached, List<LlmRequest.Message> historyMessages, boolean contextDependent) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return false;
        }
        return cached.slotMissing()
                || cached.summaryMissing()
                || cached.summaryContext() == null
                || cached.summaryContext().getFacts() == null
                || contextDependent;
    }

    private void writeContext(Long userId, Long sessionId, AiSessionContext sessionContext, SessionContextMeta meta) {
        set(slotKey(userId, sessionId), JSON.toJSONString(sessionContext.getSlotState()));
        set(summaryKey(userId, sessionId), JSON.toJSONString(sessionContext.getSummaryContext()));
        set(lastTurnsKey(userId, sessionId), JSON.toJSONString(sessionContext.getRecentTurns()));
        set(metaKey(userId, sessionId), JSON.toJSONString(meta));
    }

    private SessionContextMeta incrementMeta(SessionContextMeta meta) {
        int currentTurn = meta == null ? 0 : meta.getTurnCount();
        return SessionContextMeta.builder()
                .turnCount(currentTurn + 1)
                .lastActiveTime(System.currentTimeMillis())
                .build();
    }

    private List<LlmRequest.Message> resolveRecentTurns(CachedContext cached, Long sessionId) {
        if (cached.recentTurns() != null && !cached.recentTurns().isEmpty()) {
            return cached.recentTurns();
        }
        if (!cached.recentTurnsMissing()) {
            return cached.recentTurns() == null ? List.of() : cached.recentTurns();
        }
        return toMessages(chatMemoryService.getRecentTurns(sessionId, getLastTurns()));
    }

    private boolean shouldExtractSlot(String userMessage,
                                      List<LlmRequest.Message> recentTurns,
                                      SessionSlotState currentSlot,
                                      boolean contextDependent) {
        if (contextDependent
                || AiBusinessContextSignals.hasBusinessSlot(currentSlot)
                || AiBusinessContextSignals.hasRecentBusinessContext(recentTurns)) {
            return true;
        }
        return AiBusinessContextSignals.hasBusinessSignal(userMessage);
    }

    private List<LlmRequest.Message> recentFrom(List<LlmRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int maxMessages = getLastTurns() * 2;
        if (messages.size() <= maxMessages) {
            return messages;
        }
        return messages.subList(messages.size() - maxMessages, messages.size());
    }

    private List<LlmRequest.Message> toMessages(List<AiMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .map(message -> LlmRequest.Message.builder()
                        .role(normalizeRole(message.getRole()))
                        .content(message.getContent())
                        .build())
                .toList();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return switch (normalized) {
            case "user", "assistant", "system", "tool" -> normalized;
            default -> "user";
        };
    }

    private <T> ParseResult<T> parse(String json, Class<T> clazz, T defaultValue) {
        if (json == null || json.isBlank()) {
            return new ParseResult<>(defaultValue, false);
        }
        try {
            return new ParseResult<>(JSON.parseObject(json, clazz), false);
        } catch (Exception ex) {
            log.warn("Failed to parse session context cache: type={}, error={}", clazz.getSimpleName(), ex.getMessage());
            return new ParseResult<>(defaultValue, true);
        }
    }

    private ParseResult<List<LlmRequest.Message>> parseMessageList(String json) {
        if (json == null || json.isBlank()) {
            return new ParseResult<>(List.of(), false);
        }
        try {
            return new ParseResult<>(JSON.parseArray(json, LlmRequest.Message.class), false);
        } catch (Exception ex) {
            log.warn("Failed to parse recent turns cache: error={}", ex.getMessage());
            return new ParseResult<>(List.of(), true);
        }
    }

    private List<String> multiGet(List<String> keys) {
        try {
            return stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception ex) {
            log.warn("Failed to read session context cache: keys={}, error={}", keys, ex.getMessage());
            return List.of();
        }
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || values.size() <= index) {
            return null;
        }
        return values.get(index);
    }

    private void set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(getContextTtlSeconds()));
        } catch (Exception ex) {
            log.warn("Failed to write session context cache: key={}, error={}", key, ex.getMessage());
        }
    }

    private String slotKey(Long userId, Long sessionId) {
        return String.format(SLOT_KEY_TEMPLATE, userId, sessionId);
    }

    private String summaryKey(Long userId, Long sessionId) {
        return String.format(SUMMARY_KEY_TEMPLATE, userId, sessionId);
    }

    private String lastTurnsKey(Long userId, Long sessionId) {
        return String.format(LAST_TURNS_KEY_TEMPLATE, userId, sessionId);
    }

    private String metaKey(Long userId, Long sessionId) {
        return String.format(META_KEY_TEMPLATE, userId, sessionId);
    }

    private int getContextTtlSeconds() {
        AiProperties.Chat chat = aiProperties.getChat();
        return chat == null ? DEFAULT_CONTEXT_TTL_SECONDS : chat.getContextTtlSeconds();
    }

    private int getLastTurns() {
        AiProperties.Chat chat = aiProperties.getChat();
        return chat == null ? DEFAULT_LAST_TURNS : chat.getLastTurns();
    }

    private int getSummaryStartTurn() {
        AiProperties.Chat chat = aiProperties.getChat();
        return chat == null ? DEFAULT_SUMMARY_START_TURN : chat.getSummaryStartTurn();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedContext(SessionSlotState slotState,
                                 SessionSummaryContext summaryContext,
                                 List<LlmRequest.Message> recentTurns,
                                 SessionContextMeta meta,
                                 boolean recovered,
                                 boolean slotMissing,
                                 boolean summaryMissing,
                                 boolean recentTurnsMissing) {
    }

    private record SlotExtractionInput(String userMessage,
                                       List<LlmRequest.Message> recentTurns,
                                       SessionSlotState currentSlot,
                                       Long userId,
                                       Long sessionId,
                                       boolean contextDependent) {
    }

    private record SummaryExtractionInput(Long userId,
                                          Long sessionId,
                                          SessionSummaryContext currentSummary,
                                          SessionSlotState slotState,
                                          List<LlmRequest.Message> recentTurns,
                                          AiChatMessage userMessage,
                                          SessionContextMeta meta) {
    }

    private record ParseResult<T>(T value, boolean invalid) {
    }
}
