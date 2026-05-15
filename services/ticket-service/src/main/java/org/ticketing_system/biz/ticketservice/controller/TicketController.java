package org.ticketing_system.biz.ticketservice.controller;

import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.ticketservice.dto.req.CancelTicketOrderReqDTO;
import org.ticketing_system.biz.ticketservice.dto.req.PurchaseTicketReqDTO;
import org.ticketing_system.biz.ticketservice.dto.req.RefundTicketReqDTO;
import org.ticketing_system.biz.ticketservice.dto.req.TicketPageQueryReqDTO;
import org.ticketing_system.biz.ticketservice.dto.resp.RefundTicketRespDTO;
import org.ticketing_system.biz.ticketservice.dto.resp.TicketPageQueryRespDTO;
import org.ticketing_system.biz.ticketservice.dto.resp.TicketPurchaseRespDTO;
import org.ticketing_system.biz.ticketservice.remote.dto.PayInfoRespDTO;
import org.ticketing_system.biz.ticketservice.service.TicketService;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.ticketing_system.framework.starter.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 车票控制层
 * @author lin667z
 */
@RestController
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * 根据条件查询车票
     */
    @GetMapping("/api/ticket-service/ticket/query")
    public Result<TicketPageQueryRespDTO> pageListTicketQuery(TicketPageQueryReqDTO requestParam) {
        return Results.success(ticketService.pageListTicketQuery(requestParam));
    }

    /**
     * 购买车票
     */
    @PostMapping("/api/ticket-service/ticket/purchase")
    public Result<TicketPurchaseRespDTO> purchaseTickets(@RequestBody PurchaseTicketReqDTO requestParam) {
        return Results.success(ticketService.purchaseTickets(requestParam));
    }

    /**
     * 取消车票订单
     */
    @PostMapping("/api/ticket-service/ticket/cancel")
    public Result<Void> cancelTicketOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        ticketService.cancelTicketOrder(requestParam);
        return Results.success();
    }

    /**
     * 支付单详情查询
     */
    @GetMapping("/api/ticket-service/ticket/pay/query")
    public Result<PayInfoRespDTO> getPayInfo(@RequestParam(value = "orderSn") String orderSn) {
        return Results.success(ticketService.getPayInfo(orderSn));
    }

    /**
     * 公共退款接口
     */
    @PostMapping("/api/ticket-service/ticket/refund")
    public Result<RefundTicketRespDTO> commonTicketRefund(@RequestBody RefundTicketReqDTO requestParam) {
        return Results.success(ticketService.commonTicketRefund(requestParam));
    }
}


