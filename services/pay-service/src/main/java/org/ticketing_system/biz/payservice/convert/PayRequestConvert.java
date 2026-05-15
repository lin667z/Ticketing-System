package org.ticketing_system.biz.payservice.convert;

import org.ticketing_system.biz.payservice.common.enums.PayChannelEnum;
import org.ticketing_system.biz.payservice.dto.PayCommand;
import org.ticketing_system.biz.payservice.dto.base.AliPayRequest;
import org.ticketing_system.biz.payservice.dto.base.PayRequest;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;

import java.util.Objects;

/**
 * 支付请求入参转换器
 * @author lin667z
 */
public final class PayRequestConvert {

    /**
     * {@link PayCommand} to {@link PayRequest}
     *
     * @param payCommand 支付请求参数
     * @return {@link PayRequest}
     */
    public static PayRequest command2PayRequest(PayCommand payCommand) {
        PayRequest payRequest = null;
        if (Objects.equals(payCommand.getChannel(), PayChannelEnum.ALI_PAY.getCode())) {
            payRequest = BeanUtil.convert(payCommand, AliPayRequest.class);
        }
        return payRequest;
    }
}


