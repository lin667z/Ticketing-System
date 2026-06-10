package org.ticketing_system.biz.aiservice.memory.knowledge;

import java.util.List;

/**
 * 知识/规则检索接口（L5，RAG 预留）。
 *
 * <p>用于在对话中按用户问题检索铁路业务规则片段（退改签、计价、证件、儿童票、乘车规则等），
 * 注入到 LLM 上下文。当前为预留能力，实现可由关键词检索逐步演进为向量检索。</p>
 */
public interface KnowledgeRetriever {

    /**
     * 按查询检索相关规则片段。
     *
     * @param userId 用户 ID（可用于个性化/权限过滤，预留）
     * @param query  用户查询文本
     * @param topK   返回片段上限
     * @return 规则正文片段列表；无命中或检索关闭时返回空列表（绝不返回 null）
     */
    List<String> retrieve(Long userId, String query, int topK);
}
