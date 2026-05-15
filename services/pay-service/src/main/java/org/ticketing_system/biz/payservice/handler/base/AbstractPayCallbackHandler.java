package org.ticketing_system.biz.payservice.handler.base;

import org.ticketing_system.biz.payservice.dto.base.PayCallbackRequest;

/**
 * 抽象支付回调组件
 * @author lin667z
 */
public abstract class AbstractPayCallbackHandler {

    /**
     * 支付回调抽象接口
     *
     * @param payCallbackRequest 支付回调请求参数
     */
    public abstract void callback(PayCallbackRequest payCallbackRequest);
}


