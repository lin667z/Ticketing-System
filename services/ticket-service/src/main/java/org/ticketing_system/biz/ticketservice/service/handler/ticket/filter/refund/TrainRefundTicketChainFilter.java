package org.ticketing_system.biz.ticketservice.service.handler.ticket.filter.refund;

import org.ticketing_system.biz.ticketservice.common.enums.TicketChainMarkEnum;
import org.ticketing_system.biz.ticketservice.dto.req.RefundTicketReqDTO;
import org.ticketing_system.framework.starter.designpattern.chain.AbstractChainHandler;

/**
 * 列车车票退款过滤器
 * @author lin667z
 */
public interface TrainRefundTicketChainFilter<T extends RefundTicketReqDTO> extends AbstractChainHandler<RefundTicketReqDTO> {

    @Override
    default String mark() {
        return TicketChainMarkEnum.TRAIN_REFUND_TICKET_FILTER.name();
    }
}


