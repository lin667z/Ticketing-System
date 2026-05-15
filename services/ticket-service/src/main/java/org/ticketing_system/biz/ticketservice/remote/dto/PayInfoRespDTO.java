package org.ticketing_system.biz.ticketservice.remote.dto;

import lombok.Data;

import java.util.Date;

/**
 * 支付单详情信息返回参数
 * @author lin667z
 */
@Data
public class PayInfoRespDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 支付总金额
     */
    private Integer totalAmount;

    /**
     * 支付状态
     */
    private Integer status;

    /**
     * 支付时间
     */
    private Date gmtPayment;
}


