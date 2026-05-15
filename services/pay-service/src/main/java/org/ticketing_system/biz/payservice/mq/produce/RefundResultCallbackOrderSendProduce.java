package org.ticketing_system.biz.payservice.mq.produce;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.ticketing_system.biz.payservice.mq.domain.MessageWrapper;
import org.ticketing_system.biz.payservice.mq.event.RefundResultCallbackOrderEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static org.ticketing_system.biz.payservice.common.constant.PayRocketMQConstant.PAY_GLOBAL_TOPIC_KEY;
import static org.ticketing_system.biz.payservice.common.constant.PayRocketMQConstant.REFUND_RESULT_CALLBACK_TAG_KEY;

import java.util.UUID;

/**
 * 退款结果回调订单生产者
 * @author lin667z
 */
@Slf4j
@Component
public class RefundResultCallbackOrderSendProduce extends AbstractCommonSendProduceTemplate<RefundResultCallbackOrderEvent> {

    private final ConfigurableEnvironment environment;

    public RefundResultCallbackOrderSendProduce(@Autowired RocketMQTemplate rocketMQTemplate, @Autowired ConfigurableEnvironment environment) {
        super(rocketMQTemplate);
        this.environment = environment;
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendParam(RefundResultCallbackOrderEvent messageSendEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("全部退款或部分退款结果回调订单")
                .keys(messageSendEvent.getOrderSn())
                .topic(environment.resolvePlaceholders(PAY_GLOBAL_TOPIC_KEY))
                .tag(environment.resolvePlaceholders(REFUND_RESULT_CALLBACK_TAG_KEY))
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(RefundResultCallbackOrderEvent messageSendEvent, BaseSendExtendDTO requestParam) {
        String keys = StrUtil.isEmpty(requestParam.getKeys()) ? UUID.randomUUID().toString() : requestParam.getKeys();
        return MessageBuilder
                .withPayload(new MessageWrapper(requestParam.getKeys(), messageSendEvent))
                .setHeader(MessageConst.PROPERTY_KEYS, keys)
                .setHeader(MessageConst.PROPERTY_TAGS, requestParam.getTag())
                .build();
    }
}


