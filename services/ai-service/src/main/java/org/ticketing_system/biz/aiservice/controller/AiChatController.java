package org.ticketing_system.biz.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing_system.biz.aiservice.dto.req.AiChatReqDTO;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.service.AiChatService;
import reactor.core.publisher.Flux;

/**
 * AI 聊天控制层
 */
@RestController
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 流式对话接口
     */
    @PostMapping(value = "/api/ai-service/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<AiStreamChunk>>> streamChat(@RequestBody @Valid AiChatReqDTO requestParam) {
        // 设置响应头，禁用缓存并保持长连接
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set("X-Accel-Buffering", "no");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(aiChatService.streamChat(requestParam)
                        .map(chunk -> ServerSentEvent.builder(chunk)
                                .event(resolveEventName(chunk))
                                .build()));
    }

    /**
     * 解析事件名称
     */
    private String resolveEventName(AiStreamChunk chunk) {
        if (chunk == null || chunk.getEventType() == null) {
            return "message";
        }
        return chunk.getEventType() == AiStreamEventType.ERROR ? "error" : "message";
    }
}
