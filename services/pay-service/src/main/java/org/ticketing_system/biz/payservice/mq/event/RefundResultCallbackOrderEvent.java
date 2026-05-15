package org.ticketing_system.biz.payservice.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.payservice.common.enums.RefundTypeEnum;
import org.ticketing_system.biz.payservice.remote.dto.TicketOrderPassengerDetailRespDTO;

import java.util.List;

/**
 * 退款结果回调订单服务事件
 * @author lin667z
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class RefundResultCallbackOrderEvent {
    /**
     * 订单号
     */
    private String orderSn;
    /**
     * 退款类型
     */
    private RefundTypeEnum refundTypeEnum;

    /**
     * 部分退款车票详情
     */
    private List<TicketOrderPassengerDetailRespDTO> partialRefundTicketDetailList;
}


