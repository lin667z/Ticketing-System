package org.ticketing_system.biz.orderservice.service;

import org.ticketing_system.biz.orderservice.dto.domain.OrderStatusReversalDTO;
import org.ticketing_system.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderSelfPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import org.ticketing_system.biz.orderservice.mq.event.PayResultCallbackOrderEvent;
import org.ticketing_system.framework.starter.convention.page.PageResponse;

import java.util.Map;

/**
 * 订单接口层
 * @author lin667z
 */
public interface OrderService {

    /**
     * 跟据订单号查询车票订单
     *
     * @param orderSn 订单号
     * @return 订单详情
     */
    TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn);

    /**
     * 跟据用户名分页查询车票订单
     *
     * @param requestParam 跟据用户 ID 分页查询对象
     * @return 订单分页详情
     */
    PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    /**
     * 创建火车票订单
     *
     * @param requestParam 商品订单入参
     * @return 订单号
     */
    String createTicketOrder(TicketOrderCreateReqDTO requestParam);

    /**
     * 关闭火车票订单
     *
     * @param requestParam 关闭火车票订单入参
     */
    boolean closeTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 取消火车票订单
     *
     * @param requestParam 取消火车票订单入参
     */
    boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 订单状态反转
     *
     * @param requestParam 请求参数
     */
    void statusReversal(OrderStatusReversalDTO requestParam);

    /**
     * 支付结果回调订单
     *
     * @param requestParam 请求参数
     */
    void payCallbackOrder(PayResultCallbackOrderEvent requestParam);

    /**
     * 查询本人车票订单（30天硬上限，LIMIT 模式，非分页）
     *
     * @param requestParam 请求参数
     * @return map 包含 records（订单列表）和可选的 message（提示信息）
     */
    Map<String, Object> querySelfTicketOrders(TicketOrderSelfPageQueryReqDTO requestParam);
}


