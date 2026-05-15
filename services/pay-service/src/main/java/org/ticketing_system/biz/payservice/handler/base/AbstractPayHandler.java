package org.ticketing_system.biz.payservice.handler.base;

import org.ticketing_system.biz.payservice.dto.base.PayRequest;
import org.ticketing_system.biz.payservice.dto.base.PayResponse;

/**
 * 抽象支付组件
 * @author lin667z
 */
public abstract class AbstractPayHandler {

    /**
     * 支付抽象接口
     *
     * @param payRequest 支付请求参数
     * @return 支付响应参数
     */
    public abstract PayResponse pay(PayRequest payRequest);
}


