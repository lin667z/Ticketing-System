package org.ticketing_system.biz.ticketservice.service.handler.ticket.filter.purchase;

import org.ticketing_system.biz.ticketservice.common.enums.TicketChainMarkEnum;
import org.ticketing_system.biz.ticketservice.dto.req.PurchaseTicketReqDTO;
import org.ticketing_system.framework.starter.designpattern.chain.AbstractChainHandler;

/**
 * 列车购买车票过滤器
 * @author lin667z
 */
public interface TrainPurchaseTicketChainFilter<T extends PurchaseTicketReqDTO> extends AbstractChainHandler<PurchaseTicketReqDTO> {

    @Override
    default String mark() {
        return TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name();
    }
}


