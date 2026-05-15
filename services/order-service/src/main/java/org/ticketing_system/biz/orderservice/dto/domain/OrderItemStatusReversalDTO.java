package org.ticketing_system.biz.orderservice.dto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemDO;

import java.util.List;

/**
 * 子订单状态反转实体
 * @author lin667z
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemStatusReversalDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 订单反转后状态
     */
    private Integer orderStatus;

    /**
     * 订单明细反转后状态
     */
    private Integer orderItemStatus;

    /**
     * 订单明细集合
     */
    private List<OrderItemDO> orderItemDOList;
}


