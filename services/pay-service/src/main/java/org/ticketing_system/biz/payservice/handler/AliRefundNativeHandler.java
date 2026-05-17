package org.ticketing_system.biz.payservice.handler;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ticketing_system.biz.payservice.common.enums.PayChannelEnum;
import org.ticketing_system.biz.payservice.common.enums.PayTradeTypeEnum;
import org.ticketing_system.biz.payservice.common.enums.TradeStatusEnum;
import org.ticketing_system.biz.payservice.config.AliPayProperties;
import org.ticketing_system.biz.payservice.dto.base.AliRefundRequest;
import org.ticketing_system.biz.payservice.dto.base.RefundRequest;
import org.ticketing_system.biz.payservice.dto.base.RefundResponse;
import org.ticketing_system.biz.payservice.handler.base.AbstractRefundHandler;
import org.ticketing_system.framework.starter.convention.exception.ServiceException;
import org.ticketing_system.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.ticketing_system.framework.starter.distributedid.toolkit.SnowflakeIdUtil;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 阿里支付组件
 * @author lin667z
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliRefundNativeHandler extends AbstractRefundHandler implements AbstractExecuteStrategy<RefundRequest, RefundResponse> {

    private final AliPayProperties aliPayProperties;
    private final AlipayClient alipayClient;

    private final static String SUCCESS_CODE = "10000";

    private final static String FUND_CHANGE = "Y";

    @Retryable(value = {ServiceException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 1.5))
    @Override
    public RefundResponse refund(RefundRequest payRequest) {
        AliRefundRequest aliRefundRequest = payRequest.getAliRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(aliRefundRequest.getOrderSn());
        model.setTradeNo(aliRefundRequest.getTradeNo());
        BigDecimal payAmount = aliRefundRequest.getPayAmount();
        BigDecimal refundAmount = payAmount.divide(new BigDecimal(100));
        model.setRefundAmount(refundAmount.toString());
        model.setOutRequestNo(SnowflakeIdUtil.nextIdStr());
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            String responseJson = JSONObject.toJSONString(response);
            log.info("发起支付宝退款，订单号：{}，交易凭证号：{}，退款金额：{} \n调用退款响应：\n\n{}\n",
                    aliRefundRequest.getOrderSn(),
                    aliRefundRequest.getTradeNo(),
                    aliRefundRequest.getPayAmount(),
                    responseJson);
            if (!StrUtil.equals(SUCCESS_CODE, response.getCode()) || !StrUtil.equals(FUND_CHANGE, response.getFundChange())) {
                throw new ServiceException("退款失败");
            }
            return new RefundResponse(TradeStatusEnum.TRADE_CLOSED.tradeCode(), response.getTradeNo());
        } catch (AlipayApiException e) {
            throw new ServiceException("调用支付宝退款异常");
        }
    }

    @Override
    public String mark() {
        return StrBuilder.create()
                .append(PayChannelEnum.ALI_PAY.name())
                .append("_")
                .append(PayTradeTypeEnum.NATIVE.name())
                .append("_")
                .append(TradeStatusEnum.TRADE_CLOSED.tradeCode())
                .toString();
    }

    @Override
    public RefundResponse executeResp(RefundRequest requestParam) {
        return refund(requestParam);
    }
}


