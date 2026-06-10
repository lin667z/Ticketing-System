package org.ticketing_system.biz.aiservice.memory.longterm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;

import java.util.List;

/**
 * 记忆检索服务，支持按关键词过滤偏好
 */
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    // 默认检索 Top-K 数量
    private static final int DEFAULT_RETRIEVAL_TOP_K = 8;

    // 长期记忆服务
    private final LongTermMemoryService longTermMemoryService;
    // AI 配置属性
    private final AiProperties aiProperties;

    /**
     * 检索用户偏好，可选关键词过滤
     */
    public List<AiMemoryDO> retrieve(Long userId, String queryKeyword) {
        List<AiMemoryDO> all = longTermMemoryService.listByUserId(userId);
        if (queryKeyword != null && !queryKeyword.isBlank()) {
            String keyword = queryKeyword.toLowerCase();
            all = all.stream()
                    .filter(m -> matchesKeyword(m, keyword))
                    .toList();
        }
        int topK = getRetrievalTopK();
        if (all.size() > topK) {
            all = all.subList(0, topK);
        }
        return all;
    }

    /**
     * 判断记忆记录是否匹配关键词
     */
    private boolean matchesKeyword(AiMemoryDO memory, String keyword) {
        if (memory.getMemoryKey() != null && memory.getMemoryKey().toLowerCase().contains(keyword)) {
            return true;
        }
        if (memory.getMemoryContent() != null && memory.getMemoryContent().toLowerCase().contains(keyword)) {
            return true;
        }
        if (memory.getPreferenceValue() != null && memory.getPreferenceValue().toLowerCase().contains(keyword)) {
            return true;
        }
        return false;
    }

    /**
     * 获取配置的检索 Top-K 值
     */
    private int getRetrievalTopK() {
        AiProperties.LongTermMemory ltm = aiProperties.getLongTermMemory();
        return ltm == null ? DEFAULT_RETRIEVAL_TOP_K : ltm.getRetrievalTopK();
    }
}
