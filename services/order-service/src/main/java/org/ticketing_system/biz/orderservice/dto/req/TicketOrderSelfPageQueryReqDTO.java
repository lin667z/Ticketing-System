package org.ticketing_system.biz.orderservice.dto.req;

import lombok.Data;

/**
 * 本人车票订单查询（非分页，LIMIT 模式）
 */
@Data
public class TicketOrderSelfPageQueryReqDTO {

    /**
     * 下单日期，格式 yyyy-MM-dd
     */
    private String date;

    /**
     * 返回条数：null=不限制，0=用户要求所有（不限制），>0=限制条数
     */
    private Long count;
}


