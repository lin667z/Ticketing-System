package org.ticketing_system.biz.orderservice.dto.req;

import lombok.Data;
import java.util.List;

/**
 * 车票子订单查询
 * @author lin667z
 */
@Data
public class TicketOrderItemQueryReqDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 子订单记录id
     */
    private List<Long> orderItemRecordIds;
}


