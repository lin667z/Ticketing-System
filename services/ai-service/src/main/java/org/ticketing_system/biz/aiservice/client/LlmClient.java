package org.ticketing_system.biz.aiservice.client;

import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.client.dto.LlmStreamResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 客户端接口，定义与大语言模型交互的标准方法
 */
public interface LlmClient {

    /**
     * 流式对话，返回包含响应分片的流
     */
    Flux<LlmStreamResponse> streamChat(LlmRequest request);
}
