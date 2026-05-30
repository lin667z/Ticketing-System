package org.ticketing_system.biz.aiservice.agent;

/**
 * 支持的子 Agent 类型枚举
 */
public enum AgentType {

    /**
     * 车票信息查询 Agent
     */
    TICKET_INFO,

    /**
     * 订单查询 Agent
     */
    ORDER_QUERY,

    /**
     * 通用聊天 Agent
     */
    GENERAL_CHAT
}
