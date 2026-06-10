package org.ticketing_system.biz.aiservice.memory.episodic;

/**
 * 会话事件类型枚举
 */
public enum SessionEventType {
    // 用户请求查询余票
    TICKET_SEARCH_REQUESTED,
    // 系统展示余票结果
    TICKET_RESULTS_DISPLAYED,
    // 用户请求查询订单
    ORDER_QUERY_REQUESTED,
    // 系统展示订单结果
    ORDER_RESULTS_DISPLAYED,
    // 用户选择了某趟车次
    USER_SELECTED_TRAIN,
    // 用户提出问题
    USER_ASKED_QUESTION,
    // 系统发起追问澄清
    SYSTEM_CLARIFIED,
    // 会话开始
    SESSION_STARTED,
    // 会话结束
    SESSION_ENDED
}
