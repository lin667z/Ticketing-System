package org.ticketing_system.biz.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderItemQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderSelfPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.ticketing_system.framework.starter.convention.page.PageResponse;
import org.ticketing_system.biz.orderservice.service.OrderItemService;
import org.ticketing_system.biz.orderservice.service.OrderService;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.ticketing_system.framework.starter.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 车票订单接口控制层
 * @author lin667z
 */
@RestController
@RequiredArgsConstructor
public class TicketOrderController {

    private final OrderService orderService;
    private final OrderItemService orderItemService;

    /**
     * 根据订单号查询车票订单
     */
    @GetMapping("/api/order-service/order/ticket/query")
    public Result<TicketOrderDetailRespDTO> queryTicketOrderByOrderSn(@RequestParam(value = "orderSn") String orderSn) {
        return Results.success(orderService.queryTicketOrderByOrderSn(orderSn));
    }

    /**
     * 根据子订单记录id查询车票子订单详情
     */
    @GetMapping("/api/order-service/order/item/ticket/query")
    public Result<List<TicketOrderPassengerDetailRespDTO>> queryTicketItemOrderById(TicketOrderItemQueryReqDTO requestParam) {
        return Results.success(orderItemService.queryTicketItemOrderById(requestParam));
    }

    /**
     * 分页查询车票订单
     */
    @GetMapping("/api/order-service/order/ticket/page")
    public Result<PageResponse<TicketOrderDetailRespDTO>> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        return Results.success(orderService.pageTicketOrder(requestParam));
    }

    /**
     * 查询本人车票订单（非分页，30天硬上限，LIMIT 模式）
     */
    @GetMapping("/api/order-service/order/ticket/self/query")
    public Result<Map<String, Object>> querySelfTicketOrders(TicketOrderSelfPageQueryReqDTO requestParam) {
        return Results.success(orderService.querySelfTicketOrders(requestParam));
    }

    /**
     * 车票订单创建
     */
    @PostMapping("/api/order-service/order/ticket/create")
    public Result<String> createTicketOrder(@RequestBody TicketOrderCreateReqDTO requestParam) {
        return Results.success(orderService.createTicketOrder(requestParam));
    }

    /**
     * 车票订单关闭
     */
    @PostMapping("/api/order-service/order/ticket/close")
    public Result<Boolean> closeTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        return Results.success(orderService.closeTickOrder(requestParam));
    }

    /**
     * 车票订单取消
     */
    @PostMapping("/api/order-service/order/ticket/cancel")
    public Result<Boolean> cancelTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        return Results.success(orderService.cancelTickOrder(requestParam));
    }
}


