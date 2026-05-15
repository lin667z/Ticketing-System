package org.ticketing_system.biz.payservice.convert;

import org.ticketing_system.biz.payservice.common.enums.PayChannelEnum;
import org.ticketing_system.biz.payservice.dto.RefundCommand;
import org.ticketing_system.biz.payservice.dto.base.AliRefundRequest;
import org.ticketing_system.biz.payservice.dto.base.RefundRequest;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;

import java.util.Objects;

/**
 * 退款请求入参转换器
 * @author lin667z
 */
public final class RefundRequestConvert {

    /**
     * {@link RefundCommand} to {@link RefundRequest}
     *
     * @param refundCommand 退款请求参数
     * @return {@link RefundRequest}
     */
    public static RefundRequest command2RefundRequest(RefundCommand refundCommand) {
        RefundRequest refundRequest = null;
        if (Objects.equals(refundCommand.getChannel(), PayChannelEnum.ALI_PAY.getCode())) {
            refundRequest = BeanUtil.convert(refundCommand, AliRefundRequest.class);
        }
        return refundRequest;
    }
}


