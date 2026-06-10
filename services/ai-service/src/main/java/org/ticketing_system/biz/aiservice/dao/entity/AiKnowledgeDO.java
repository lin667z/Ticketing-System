package org.ticketing_system.biz.aiservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;

import java.util.Date;

/**
 * AI 铁路规则知识实体（L5 RAG 预留）。
 *
 * <p>当前为占位实体，检索器 {@code RailwayRuleRetriever} 暂未使用；
 * 后期接入 RAG 时承载退改签/计价/证件/儿童票等规则文档。</p>
 */
@Data
@TableName("t_ai_knowledge")
public class AiKnowledgeDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 规则分类：退改签/计价/证件/儿童票/乘车规则
     */
    private String category;

    /**
     * 规则标题
     */
    private String title;

    /**
     * 规则正文
     */
    private String content;

    /**
     * 降级关键词检索用关键词
     */
    private String keywords;

    /**
     * 向量（预留，或落外部向量库）
     */
    private byte[] embedding;

    /**
     * 规则生效时间
     */
    private Date effectiveDate;

    /**
     * 规则失效时间
     */
    private Date expireDate;

    /**
     * 规则版本
     */
    private String version;

    /**
     * 来源
     */
    private String source;

    /**
     * 权重/优先级
     */
    private Integer weight;
}
