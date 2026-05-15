package org.ticketing_system.biz.orderservice.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.orderservice.common.enums.RefundTypeEnum;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;

import java.util.List;

/**
 * 退款结果回调订单服务事件
 * @author lin667z
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class RefundResultCallbackOrderEvent {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 退款类型枚举
     */
    private RefundTypeEnum refundTypeEnum;

    /**
     * 部分退款车票详情
     */
    private List<TicketOrderPassengerDetailRespDTO> partialRefundTicketDetailList;
}


