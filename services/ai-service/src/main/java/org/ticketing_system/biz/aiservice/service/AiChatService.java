package org.ticketing_system.biz.aiservice.service;

import org.ticketing_system.biz.aiservice.dto.req.AiChatReqDTO;
import org.ticketing_system.biz.aiservice.dto.domain.AiStreamChunk;
import reactor.core.publisher.Flux;

/**
 * AI 聊天服务接口
 */
public interface AiChatService {

    /**
     * 流式聊天
     *
     * @param requestParam 聊天请求参数
     * @return 包含 AI 响应分片的响应流
     */
    Flux<AiStreamChunk> streamChat(AiChatReqDTO requestParam);
}
