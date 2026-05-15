package org.ticketing_system.biz.payservice.dto.base;

import lombok.Getter;
import lombok.Setter;

/**
 * 抽象支付回调入参实体
 * @author lin667z
 */
public abstract class AbstractPayCallbackRequest implements PayCallbackRequest {

    @Getter
    @Setter
    private String orderRequestId;

    @Override
    public AliPayCallbackRequest getAliPayCallBackRequest() {
        return null;
    }

    @Override
    public String buildMark() {
        return null;
    }
}


