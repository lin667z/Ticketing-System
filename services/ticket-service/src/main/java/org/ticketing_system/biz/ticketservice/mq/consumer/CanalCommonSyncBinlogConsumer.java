package org.ticketing_system.biz.ticketservice.mq.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.ticketing_system.biz.ticketservice.common.constant.TicketRocketMQConstant;
import org.ticketing_system.biz.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import org.ticketing_system.biz.ticketservice.mq.event.CanalBinlogEvent;
import org.ticketing_system.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import org.ticketing_system.framework.starter.idempotent.annotation.Idempotent;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentSceneEnum;
import org.ticketing_system.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 列车车票余量缓存更新消费端
 * @author lin667z
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.CANAL_COMMON_SYNC_TOPIC_KEY,
        consumerGroup = TicketRocketMQConstant.CANAL_COMMON_SYNC_CG_KEY
)
public class CanalCommonSyncBinlogConsumer implements RocketMQListener<CanalBinlogEvent> {

    private final AbstractStrategyChoose abstractStrategyChoose;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;

    @Idempotent(
            uniqueKeyPrefix = "ticketing-system-ticket:binlog_sync:",
            key = "#message.getId()+'_'+#message.hashCode()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Override
    public void onMessage(CanalBinlogEvent message) {
        if (message.getIsDdl()
                || CollUtil.isEmpty(message.getOld())
                || !Objects.equals("UPDATE", message.getType())
                || !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            return;
        }
        abstractStrategyChoose.chooseAndExecute(
                message.getTable(),
                message,
                CanalExecuteStrategyMarkEnum.isPatternMatch(message.getTable())
        );
    }
}


