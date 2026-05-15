package org.ticketing_system.biz.payservice.convert;

import org.ticketing_system.biz.payservice.common.enums.PayChannelEnum;
import org.ticketing_system.biz.payservice.dto.PayCallbackCommand;
import org.ticketing_system.biz.payservice.dto.base.AliPayCallbackRequest;
import org.ticketing_system.biz.payservice.dto.base.PayCallbackRequest;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;

import java.util.Objects;

/**
 * 支付回调请求入参转换器
 * @author lin667z
 */
public final class PayCallbackRequestConvert {

    /**
     * {@link PayCallbackCommand} to {@link PayCallbackRequest}
     *
     * @param payCallbackCommand 支付回调请求参数
     * @return {@link PayCallbackRequest}
     */
    public static PayCallbackRequest command2PayCallbackRequest(PayCallbackCommand payCallbackCommand) {
        PayCallbackRequest payCallbackRequest = null;
        if (Objects.equals(payCallbackCommand.getChannel(), PayChannelEnum.ALI_PAY.getCode())) {
            payCallbackRequest = BeanUtil.convert(payCallbackCommand, AliPayCallbackRequest.class);
            ((AliPayCallbackRequest) payCallbackRequest).setOrderRequestId(payCallbackCommand.getOrderRequestId());
        }
        return payCallbackRequest;
    }
}


