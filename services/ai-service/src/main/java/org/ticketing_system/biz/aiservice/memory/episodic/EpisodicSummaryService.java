package org.ticketing_system.biz.aiservice.memory.episodic;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiEpisodeDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiEpisodeMapper;
import org.ticketing_system.biz.aiservice.memory.longterm.MemoryDigestService;

import java.util.List;

/**
 * 情节摘要服务，将事件持久化为情节记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodicSummaryService {

    // 情节 Mapper
    private final AiEpisodeMapper aiEpisodeMapper;
    // 事实推导服务
    private final FactDerivationService factDerivationService;
    // 记忆消化服务（情节 → 长期偏好）
    private final MemoryDigestService memoryDigestService;
    // AI 配置属性
    private final AiProperties aiProperties;

    /**
     * 终结当前情节，推导事实并持久化；若开启自动消化则异步提取长期偏好。
     */
    public AiEpisodeDO finalizeEpisode(Long userId, Long sessionId, List<SessionEvent> events, int turnStart, int turnEnd) {
        List<String> structuredFacts = factDerivationService.derive(events);

        AiEpisodeDO episode = new AiEpisodeDO();
        episode.setUserId(userId);
        episode.setSessionId(sessionId);
        episode.setEvents(JSON.toJSONString(events));
        episode.setStructuredFacts(JSON.toJSONString(structuredFacts));
        episode.setTurnStart(turnStart);
        episode.setTurnEnd(turnEnd);
        aiEpisodeMapper.insert(episode);
        log.info("Episode finalized: userId={}, sessionId={}, events={}, facts={}",
                userId, sessionId, events.size(), structuredFacts.size());

        if (isAutoDigestEnabled() && !structuredFacts.isEmpty()) {
            memoryDigestService.digestEpisode(episode);
        }
        return episode;
    }

    /**
     * 是否开启情节自动消化为长期偏好
     */
    private boolean isAutoDigestEnabled() {
        AiProperties.LongTermMemory ltm = aiProperties.getLongTermMemory();
        return ltm == null || ltm.isAutoDigestEnabled();
    }

    /**
     * 按用户查询近期情节
     */
    public List<AiEpisodeDO> queryByUserId(Long userId, int limit) {
        return aiEpisodeMapper.selectByUserId(userId, limit);
    }
}
