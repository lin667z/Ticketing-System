package org.ticketing_system.biz.payservice.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ticketing_system.biz.payservice.common.enums.PayChannelEnum;
import org.ticketing_system.biz.payservice.common.enums.TradeStatusEnum;
import org.ticketing_system.biz.payservice.dto.PayCallbackReqDTO;
import org.ticketing_system.biz.payservice.dto.base.AliPayCallbackRequest;
import org.ticketing_system.biz.payservice.dto.base.PayCallbackRequest;
import org.ticketing_system.biz.payservice.handler.base.AbstractPayCallbackHandler;
import org.ticketing_system.biz.payservice.service.PayService;
import org.ticketing_system.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.stereotype.Service;

/**
 * 阿里支付回调组件
 * @author lin667z
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class AliPayCallbackHandler extends AbstractPayCallbackHandler implements AbstractExecuteStrategy<PayCallbackRequest, Void> {

    private final PayService payService;

    @Override
    public void callback(PayCallbackRequest payCallbackRequest) {
        AliPayCallbackRequest aliPayCallBackRequest = payCallbackRequest.getAliPayCallBackRequest();
        PayCallbackReqDTO payCallbackRequestParam = PayCallbackReqDTO.builder()
                .status(TradeStatusEnum.queryActualTradeStatusCode(aliPayCallBackRequest.getTradeStatus()))
                .payAmount(aliPayCallBackRequest.getBuyerPayAmount())
                .tradeNo(aliPayCallBackRequest.getTradeNo())
                .gmtPayment(aliPayCallBackRequest.getGmtPayment())
                .orderSn(aliPayCallBackRequest.getOrderRequestId())
                .build();
        payService.callbackPay(payCallbackRequestParam);
    }

    @Override
    public String mark() {
        return PayChannelEnum.ALI_PAY.name();
    }

    public void execute(PayCallbackRequest requestParam) {
        callback(requestParam);
    }
}


