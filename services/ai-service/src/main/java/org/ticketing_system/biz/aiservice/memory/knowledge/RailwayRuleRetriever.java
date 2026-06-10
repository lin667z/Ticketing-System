package org.ticketing_system.biz.aiservice.memory.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.config.AiProperties;

import java.util.List;

/**
 * 铁路规则检索器（L5 RAG 预留实现）。
 *
 * <p>当前为占位 no-op：当 {@code ai.knowledge.enabled=false}（默认）时返回空列表，不影响主流程。
 * 后期可在此接入：</p>
 * <ul>
 *   <li>KEYWORD 模式：基于 {@code t_ai_knowledge.keywords} 的关键词倒排/全文检索；</li>
 *   <li>EMBEDDING 模式：基于 {@code t_ai_knowledge.embedding} 或外部向量库的语义检索。</li>
 * </ul>
 * 检索时应叠加 {@code effective_date/expire_date} 时效过滤与 {@code version} 取最新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RailwayRuleRetriever implements KnowledgeRetriever {

    private final AiProperties aiProperties;

    @Override
    public List<String> retrieve(Long userId, String query, int topK) {
        AiProperties.Knowledge cfg = aiProperties.getKnowledge();
        if (cfg == null || !cfg.isEnabled()) {
            return List.of();
        }
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        // TODO L5: 按 cfg.getMode() 接入关键词 / 向量检索 t_ai_knowledge，叠加时效与版本过滤。
        log.debug("Knowledge retrieval enabled but no backend wired yet: mode={}, query={}", cfg.getMode(), query);
        return List.of();
    }
}
