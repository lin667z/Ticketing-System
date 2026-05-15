package org.ticketing_system.biz.orderservice.mq.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.ticketing_system.biz.orderservice.common.constant.OrderRocketMQConstant;
import org.ticketing_system.biz.orderservice.common.enums.OrderItemStatusEnum;
import org.ticketing_system.biz.orderservice.common.enums.OrderStatusEnum;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemDO;
import org.ticketing_system.biz.orderservice.dto.domain.OrderItemStatusReversalDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.ticketing_system.biz.orderservice.mq.domain.MessageWrapper;
import org.ticketing_system.biz.orderservice.mq.event.RefundResultCallbackOrderEvent;
import org.ticketing_system.biz.orderservice.service.OrderItemService;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;
import org.ticketing_system.framework.starter.idempotent.annotation.Idempotent;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentSceneEnum;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 退款结果回调订单消费者
 * @author lin667z
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.PAY_GLOBAL_TOPIC_KEY,
        selectorExpression = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_TAG_KEY,
        consumerGroup = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_ORDER_CG_KEY
)
public class RefundResultCallbackOrderConsumer implements RocketMQListener<MessageWrapper<RefundResultCallbackOrderEvent>> {

    private final OrderItemService orderItemService;

    @Idempotent(
            uniqueKeyPrefix = "ticketing-system-order:refund_result_callback:",
            key = "#message.getKeys()+'_'+#message.hashCode()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void onMessage(MessageWrapper<RefundResultCallbackOrderEvent> message) {
        RefundResultCallbackOrderEvent refundResultCallbackOrderEvent = message.getMessage();
        Integer status = refundResultCallbackOrderEvent.getRefundTypeEnum().getCode();
        String orderSn = refundResultCallbackOrderEvent.getOrderSn();
        List<OrderItemDO> orderItemDOList = new ArrayList<>();
        List<TicketOrderPassengerDetailRespDTO> partialRefundTicketDetailList = refundResultCallbackOrderEvent.getPartialRefundTicketDetailList();
        partialRefundTicketDetailList.forEach(partial -> {
            OrderItemDO orderItemDO = new OrderItemDO();
            BeanUtil.convert(partial, orderItemDO);
            orderItemDOList.add(orderItemDO);
        });
        if (status.equals(OrderStatusEnum.PARTIAL_REFUND.getStatus())) {
            OrderItemStatusReversalDTO partialRefundOrderItemStatusReversalDTO = OrderItemStatusReversalDTO.builder()
                    .orderSn(orderSn)
                    .orderStatus(OrderStatusEnum.PARTIAL_REFUND.getStatus())
                    .orderItemStatus(OrderItemStatusEnum.REFUNDED.getStatus())
                    .orderItemDOList(orderItemDOList)
                    .build();
            orderItemService.orderItemStatusReversal(partialRefundOrderItemStatusReversalDTO);
        } else if (status.equals(OrderStatusEnum.FULL_REFUND.getStatus())) {
            OrderItemStatusReversalDTO fullRefundOrderItemStatusReversalDTO = OrderItemStatusReversalDTO.builder()
                    .orderSn(orderSn)
                    .orderStatus(OrderStatusEnum.FULL_REFUND.getStatus())
                    .orderItemStatus(OrderItemStatusEnum.REFUNDED.getStatus())
                    .orderItemDOList(orderItemDOList)
                    .build();
            orderItemService.orderItemStatusReversal(fullRefundOrderItemStatusReversalDTO);
        }
    }
}


