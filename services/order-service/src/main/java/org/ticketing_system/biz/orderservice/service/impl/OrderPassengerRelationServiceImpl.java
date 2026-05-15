package org.ticketing_system.biz.orderservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemPassengerDO;
import org.ticketing_system.biz.orderservice.dao.mapper.OrderItemPassengerMapper;
import org.ticketing_system.biz.orderservice.service.OrderPassengerRelationService;
import org.springframework.stereotype.Service;

/**
 * 乘车人订单关系接口层实现
 * @author lin667z
 */
@Service
public class OrderPassengerRelationServiceImpl extends ServiceImpl<OrderItemPassengerMapper, OrderItemPassengerDO> implements OrderPassengerRelationService {
}


