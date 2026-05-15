package org.ticketing_system.biz.orderservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemDO;
import org.ticketing_system.biz.orderservice.dto.domain.OrderItemStatusReversalDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderItemQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import java.util.List;

/**
 * 订单明细接口层
 * @author lin667z
 */
public interface OrderItemService extends IService<OrderItemDO> {

    /**
     * 子订单状态反转
     *
     * @param requestParam 请求参数
     */
    void orderItemStatusReversal(OrderItemStatusReversalDTO requestParam);

    /**
     * 根据子订单记录id查询车票子订单详情
     *
     * @param requestParam 请求参数
     */
    List<TicketOrderPassengerDetailRespDTO> queryTicketItemOrderById(TicketOrderItemQueryReqDTO requestParam);
}


