package org.ticketing_system.biz.aiservice.session;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.memory.ChatMemoryService;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import org.ticketing_system.biz.aiservice.session.context.SessionContextMeta;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;
import org.ticketing_system.biz.aiservice.session.context.SlotExtractionResult;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSessionContextServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ChatMemoryService chatMemoryService;
    @Mock
    private SlotExtractor slotExtractor;
    @Mock
    private SlotStateMerger slotStateMerger;
    @Mock
    private SessionContextExtractor sessionContextExtractor;

    private AiSessionContextService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        service = new AiSessionContextService(
                stringRedisTemplate,
                chatMemoryService,
                slotExtractor,
                slotStateMerger,
                sessionContextExtractor,
                properties);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sessionContextExtractor.normalize(any(), any())).thenReturn(SessionSummaryContext.empty());
        lenient().when(slotStateMerger.merge(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newCasualSessionSkipsCacheHistoryAndSlotExtraction() {
        AiChatRequestContext context = newContext(true);

        AiSessionContext result = service.prepare(context, userMessage("今天天气真好啊")).block();

        assertThat(result).isNotNull();
        assertThat(result.getTurnCount()).isEqualTo(1);
        assertThat(result.getRecentTurns()).isEmpty();
        verify(valueOperations, never()).multiGet(anyList());
        verify(chatMemoryService, never()).getAllMessages(anyLong(), anyLong());
        verify(chatMemoryService, never()).getRecentTurns(anyLong(), anyInt());
        verify(slotExtractor, never()).extract(anyString(), anyList(), any(), anyLong(), anyLong());
    }

    @Test
    void newTicketLikeSessionSkipsHistoryButRunsSlotExtraction() {
        AiChatRequestContext context = newContext(true);
        when(slotExtractor.extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID)))
                .thenReturn(Mono.just(SlotExtractionResult.empty()));

        service.prepare(context, userMessage("帮我查明天去北京的高铁票")).block();

        verify(valueOperations, never()).multiGet(anyList());
        verify(chatMemoryService, never()).getAllMessages(anyLong(), anyLong());
        verify(chatMemoryService, never()).getRecentTurns(anyLong(), anyInt());
        verify(slotExtractor).extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID));
    }

    @Test
    void cachedEmptyRecentTurnsDoesNotFallbackToDatabase() {
        AiChatRequestContext context = newContext(false);
        when(valueOperations.multiGet(anyList())).thenReturn(cachedValues(
                SessionSlotState.empty(),
                SessionSummaryContext.empty(),
                SessionContextMeta.builder().turnCount(1).build(),
                List.of()));

        service.prepare(context, userMessage("今天天气真好啊")).block();

        verify(chatMemoryService, never()).getAllMessages(anyLong(), anyLong());
        verify(chatMemoryService, never()).getRecentTurns(anyLong(), anyInt());
        verify(slotExtractor, never()).extract(anyString(), anyList(), any(), anyLong(), anyLong());
    }

    @Test
    void cachedEmptySlotDoesNotTriggerRebuildByItself() {
        AiChatRequestContext context = newContext(false);
        when(valueOperations.multiGet(anyList())).thenReturn(cachedValues(
                SessionSlotState.empty(),
                SessionSummaryContext.empty(),
                SessionContextMeta.builder().turnCount(1).build(),
                List.of()));
        when(slotExtractor.extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID)))
                .thenReturn(Mono.just(SlotExtractionResult.empty()));

        service.prepare(context, userMessage("查一下G1234")).block();

        verify(chatMemoryService, never()).getAllMessages(anyLong(), anyLong());
        verify(slotExtractor, never()).rebuildFromHistory(anyList(), anyLong(), anyLong());
        verify(slotExtractor).extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID));
    }

    @Test
    void contextDependentTextFetchesHistoryAndRebuildsWhenCacheMissing() {
        AiChatRequestContext context = newContext(false);
        when(valueOperations.multiGet(anyList())).thenReturn(List.of());
        when(chatMemoryService.getAllMessages(SESSION_ID, USER_ID)).thenReturn(List.of(message("user", "帮我查北京到上海的票")));
        when(slotExtractor.rebuildFromHistory(anyList(), eq(USER_ID), eq(SESSION_ID)))
                .thenReturn(Mono.just(SlotExtractionResult.empty()));
        when(sessionContextExtractor.extract(anyList(), any(), any(), eq(USER_ID), eq(SESSION_ID)))
                .thenReturn(Mono.just(SessionSummaryContext.empty()));
        when(slotExtractor.extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID)))
                .thenReturn(Mono.just(SlotExtractionResult.empty()));

        service.prepare(context, userMessage("刚才那个改成返程")).block();

        verify(chatMemoryService).getAllMessages(SESSION_ID, USER_ID);
        verify(slotExtractor).rebuildFromHistory(anyList(), eq(USER_ID), eq(SESSION_ID));
        verify(slotExtractor).extract(anyString(), anyList(), any(), eq(USER_ID), eq(SESSION_ID));
    }

    private AiChatRequestContext newContext(boolean newSession) {
        return AiChatRequestContext.builder()
                .sessionId(SESSION_ID)
                .newSession(newSession)
                .authenticatedUser(AiAuthenticatedUserContext.builder()
                        .userId(USER_ID)
                        .username("zhangsan")
                        .build())
                .build();
    }

    private AiChatMessage userMessage(String content) {
        return AiChatMessage.user(SESSION_ID, USER_ID, content);
    }

    private AiMessageDO message(String role, String content) {
        AiMessageDO message = new AiMessageDO();
        message.setSessionId(SESSION_ID);
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private List<String> cachedValues(SessionSlotState slotState,
                                      SessionSummaryContext summaryContext,
                                      SessionContextMeta meta,
                                      List<LlmRequest.Message> recentTurns) {
        return List.of(
                JSON.toJSONString(slotState),
                JSON.toJSONString(summaryContext),
                JSON.toJSONString(meta),
                JSON.toJSONString(recentTurns));
    }
}
