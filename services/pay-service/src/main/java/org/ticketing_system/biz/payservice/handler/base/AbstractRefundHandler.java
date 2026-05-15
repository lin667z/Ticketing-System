package org.ticketing_system.biz.payservice.handler.base;

import org.ticketing_system.biz.payservice.dto.base.RefundRequest;
import org.ticketing_system.biz.payservice.dto.base.RefundResponse;

/**
 * 抽象退款组件
 * @author lin667z
 */
public abstract class AbstractRefundHandler {

    /**
     * 支付退款接口
     *
     * @param payRequest 退款请求参数
     * @return 退款响应参数
     */
    public abstract RefundResponse refund(RefundRequest payRequest);
}


