package org.ticketing_system.biz.orderservice.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ticketing_system.framework.starter.convention.page.PageRequest;

/**
 * 车票订单分页查询
 * 
 * @author lin667z
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketOrderPageQueryReqDTO extends PageRequest {

    /**
     * 状态类型 0：未完成 1：未出行 2：历史订单
     */
    private Integer statusType;
}
