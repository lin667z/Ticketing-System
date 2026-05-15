package org.ticketing_system.biz.orderservice.mq.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.ticketing_system.biz.orderservice.common.constant.OrderRocketMQConstant;
import org.ticketing_system.biz.orderservice.common.enums.OrderItemStatusEnum;
import org.ticketing_system.biz.orderservice.common.enums.OrderStatusEnum;
import org.ticketing_system.biz.orderservice.dto.domain.OrderStatusReversalDTO;
import org.ticketing_system.biz.orderservice.mq.domain.MessageWrapper;
import org.ticketing_system.biz.orderservice.mq.event.PayResultCallbackOrderEvent;
import org.ticketing_system.biz.orderservice.service.OrderService;
import org.ticketing_system.framework.starter.idempotent.annotation.Idempotent;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentSceneEnum;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付结果回调订单消费者
 * @author lin667z
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.PAY_GLOBAL_TOPIC_KEY,
        selectorExpression = OrderRocketMQConstant.PAY_RESULT_CALLBACK_TAG_KEY,
        consumerGroup = OrderRocketMQConstant.PAY_RESULT_CALLBACK_ORDER_CG_KEY
)
public class PayResultCallbackOrderConsumer implements RocketMQListener<MessageWrapper<PayResultCallbackOrderEvent>> {

    private final OrderService orderService;

    @Idempotent(
            uniqueKeyPrefix = "ticketing-system-order:pay_result_callback:",
            key = "#message.getKeys()+'_'+#message.hashCode()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void onMessage(MessageWrapper<PayResultCallbackOrderEvent> message) {
        PayResultCallbackOrderEvent payResultCallbackOrderEvent = message.getMessage();
        OrderStatusReversalDTO orderStatusReversalDTO = OrderStatusReversalDTO.builder()
                .orderSn(payResultCallbackOrderEvent.getOrderSn())
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build();
        orderService.statusReversal(orderStatusReversalDTO);
        orderService.payCallbackOrder(payResultCallbackOrderEvent);
    }
}


