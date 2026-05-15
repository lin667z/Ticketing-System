package org.ticketing_system.biz.payservice.dto;

import lombok.Data;
import org.ticketing_system.biz.payservice.dto.base.AbstractRefundRequest;

import java.math.BigDecimal;

/**
 * 退款请求命令
 * @author lin667z
 */
@Data
public final class RefundCommand extends AbstractRefundRequest {

    /**
     * 支付金额
     */
    private BigDecimal payAmount;

    /**
     * 交易凭证号
     */
    private String tradeNo;
}


