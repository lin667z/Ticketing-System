package org.ticketing_system.biz.aiservice.session.context;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化业务上下文，作为多轮对话的执行数据源，日期字段存储的是归一化后的 yyyy-MM-dd。
 *
 * <p>支持同一会话内并行的多条票务线路（如「北京→杭州」与「广州→湖北」同时存在），
 * 每条线路以 {@link TicketSlot#getRouteKey()} 作为稳定标识，跨轮补参时按 routeKey 精确匹配，
 * 避免不同线路互相污染。订单查询为用户本人维度，单槽位即可。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSlotState {

    /**
     * 当前会话并行存在的票务线路槽位列表，每条线路独立维护
     */
    @Builder.Default
    private List<TicketSlot> ticketRoutes = new ArrayList<>();

    /**
     * 订单查询相关的槽位信息，用于多轮对话中逐步填充用户订单查询条件
     */
    @Builder.Default
    private OrderQuerySlot orderQuery = new OrderQuerySlot();

    /**
     * 创建一个所有槽位均为初始状态的空实例
     *
     * @return 空的 SessionSlotState 实例
     */
    public static SessionSlotState empty() {
        return SessionSlotState.builder()
                .ticketRoutes(new ArrayList<>())
                .orderQuery(new OrderQuerySlot())
                .build();
    }

    /**
     * 返回唯一线路（仅当恰好存在一条线路时），用于「那后天呢」这类无线路指向的跟进消息的兜底回填。
     * 多条或零条线路时返回 null，迫使上游显式区分线路。
     */
    @JSONField(serialize = false, deserialize = false)
    public TicketSlot getSoleRouteOrNull() {
        return ticketRoutes != null && ticketRoutes.size() == 1 ? ticketRoutes.get(0) : null;
    }

    /**
     * 按 routeKey 查找已有线路槽位
     */
    @JSONField(serialize = false, deserialize = false)
    public TicketSlot findRoute(String routeKey) {
        if (routeKey == null || ticketRoutes == null) {
            return null;
        }
        for (TicketSlot slot : ticketRoutes) {
            if (slot != null && routeKey.equals(slot.getRouteKey())) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 是否存在任意有内容的票务线路
     */
    @JSONField(serialize = false, deserialize = false)
    public boolean hasAnyTicketRoute() {
        if (ticketRoutes == null) {
            return false;
        }
        for (TicketSlot slot : ticketRoutes) {
            if (slot != null && slot.hasAnyField()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 购票槽位，存储用户购票时提取的结构化信息。一条线路一个实例。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSlot {

        // 线路稳定标识，规则为 "出发地->到达地"（归一化前的原始名），用于跨轮按线路匹配
        private String routeKey;

        // 出发城市/站点
        private String departure;

        // 到达城市/站点
        private String arrival;

        // 出发日期，格式为 yyyy-MM-dd
        private String date;

        // 车次编号
        private String trainNumber;

        // 座位类型（如二等座、一等座、商务座等）
        private String seatType;

        // 购票人数
        private Integer passengerCount;

        /**
         * 依据出发地、到达地计算线路标识
         */
        public static String routeKeyOf(String departure, String arrival) {
            String dep = departure == null ? "" : departure.trim();
            String arr = arrival == null ? "" : arrival.trim();
            if (dep.isEmpty() && arr.isEmpty()) {
                return null;
            }
            return dep + "->" + arr;
        }

        @JSONField(serialize = false, deserialize = false)
        public boolean hasAnyField() {
            return notBlank(departure) || notBlank(arrival) || notBlank(date) || notBlank(trainNumber);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * 订单查询槽位，存储用户查询订单时提取的结构化信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderQuerySlot {

        // 乘车日期，格式为 yyyy-MM-dd
        private String date;

        // 订单数量
        private Long count;

        // 订单状态
        private String status;

        // 车次编号
        private String trainNumber;

        // 出发地
        private String departure;

        // 目的地
        private String arrival;

        // 乘客姓名
        private String passengerName;
    }
}
