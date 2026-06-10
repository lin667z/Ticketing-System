package org.ticketing_system.biz.aiservice.memory.longterm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.dao.entity.AiEpisodeDO;
import org.ticketing_system.biz.aiservice.agent.core.AgentLlmService;
import org.ticketing_system.biz.aiservice.common.util.JsonExtractor;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;

/**
 * 记忆消化服务，通过 LLM 从情节事实中提取偏好并持久化为长期记忆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryDigestService {

    // 长期记忆服务
    private final LongTermMemoryService longTermMemoryService;
    // LLM 调用服务
    private final AgentLlmService agentLlmService;

    /**
     * 异步消化情节，提取并持久化偏好。
     */
    public void digestEpisode(AiEpisodeDO episode) {
        if (episode == null || episode.getStructuredFacts() == null) {
            return;
        }
        log.info("Digesting episode {} for user {}", episode.getId(), episode.getUserId());

        String prompt = buildDigestPrompt(episode);
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(buildDigestSystemPrompt())
                .userMessage(prompt)
                .userId(episode.getUserId())
                .sessionId(episode.getSessionId())
                .maxTokens(256)
                .build();

        agentLlmService.complete(request)
                .subscribe(
                        response -> persistPreferences(episode, response.getContent()),
                        error -> log.warn("Episode {} digest failed: {}", episode.getId(), error.getMessage())
                );
    }

    /**
     * 解析 LLM 输出的偏好 JSON 并逐条持久化。
     */
    private void persistPreferences(AiEpisodeDO episode, String content) {
        if (content == null || content.isBlank()) {
            log.info("Episode {} digest produced no content", episode.getId());
            return;
        }
        int saved = 0;
        try {
            String json = JsonExtractor.firstJsonObject(content);
            JSONObject root = JSON.parseObject(json);
            JSONArray preferences = root == null ? null : root.getJSONArray("preferences");
            if (preferences == null || preferences.isEmpty()) {
                log.info("Episode {} digest extracted no preferences", episode.getId());
                return;
            }
            for (int i = 0; i < preferences.size(); i++) {
                JSONObject item = preferences.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (savePreferenceItem(episode, item)) {
                    saved++;
                }
            }
        } catch (Exception ex) {
            log.warn("Episode {} digest parse failed: content={}, error={}", episode.getId(), content, ex.getMessage());
            return;
        }
        log.info("Episode {} digest complete for user {}: savedPreferences={}", episode.getId(), episode.getUserId(), saved);
    }

    /**
     * 持久化单条偏好；类型非法或字段缺失则跳过。
     */
    private boolean savePreferenceItem(AiEpisodeDO episode, JSONObject item) {
        String typeRaw = item.getString("type");
        String key = item.getString("key");
        String value = item.getString("value");
        if (typeRaw == null || typeRaw.isBlank() || key == null || key.isBlank()) {
            return false;
        }
        PreferenceType type;
        try {
            type = PreferenceType.valueOf(typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Skip preference with unknown type: {}", typeRaw);
            return false;
        }
        String content = key + (value == null ? "" : "=" + value);
        longTermMemoryService.savePreference(episode.getUserId(), type, key.trim(), value, content, "AUTO_DIGEST");
        return true;
    }

    /**
     * 构建偏好提取系统提示词
     */
    private String buildDigestSystemPrompt() {
        return "你是铁路出行偏好分析器。从用户对话中提取出行偏好，输出 JSON 格式："
                + "{\"preferences\": [{\"type\": \"ROUTE/TIME_WINDOW/SEAT_CLASS/BUDGET/TRAIN_TYPE/STATION/CUSTOM\","
                + " \"key\": \"偏好键\", \"value\": \"偏好值\"}]}。"
                + "只输出 JSON，不要 Markdown，不要解释；没有明确偏好时返回 {\"preferences\":[]}。";
    }

    /**
     * 构建基于情节事实的用户提示词
     */
    private String buildDigestPrompt(AiEpisodeDO episode) {
        return "从以下会话事实中提取用户出行偏好：\n" + episode.getStructuredFacts();
    }
}
