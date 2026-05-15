package org.ticketing_system.biz.ticketservice.service.handler.ticket.filter.query;

import org.ticketing_system.biz.ticketservice.common.enums.TicketChainMarkEnum;
import org.ticketing_system.biz.ticketservice.dto.req.TicketPageQueryReqDTO;
import org.ticketing_system.framework.starter.designpattern.chain.AbstractChainHandler;

/**
 * 列车车票查询过滤器
 * @author lin667z
 */
public interface TrainTicketQueryChainFilter<T extends TicketPageQueryReqDTO> extends AbstractChainHandler<TicketPageQueryReqDTO> {

    @Override
    default String mark() {
        return TicketChainMarkEnum.TRAIN_QUERY_FILTER.name();
    }
}


