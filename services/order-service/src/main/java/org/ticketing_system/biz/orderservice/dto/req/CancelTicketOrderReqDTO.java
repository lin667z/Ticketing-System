package org.ticketing_system.biz.orderservice.dto.req;

import lombok.Data;

/**
 * 取消车票订单请求入参
 * @author lin667z
 */
@Data
public class CancelTicketOrderReqDTO {

    /**
     * 订单号
     */
    private String orderSn;
}


