package org.ticketing_system.biz.orderservice.service.impl;

import cn.crane4j.annotation.AutoOperate;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.text.StrBuilder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.ticketing_system.biz.orderservice.common.enums.OrderCanalErrorCodeEnum;
import org.ticketing_system.biz.orderservice.common.enums.OrderItemStatusEnum;
import org.ticketing_system.biz.orderservice.common.enums.OrderStatusEnum;
import org.ticketing_system.biz.orderservice.dao.entity.OrderDO;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemDO;
import org.ticketing_system.biz.orderservice.dao.entity.OrderItemPassengerDO;
import org.ticketing_system.biz.orderservice.dao.mapper.OrderItemMapper;
import org.ticketing_system.biz.orderservice.dao.mapper.OrderMapper;
import org.ticketing_system.biz.orderservice.dto.domain.OrderStatusReversalDTO;
import org.ticketing_system.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderItemCreateReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.req.TicketOrderSelfPageQueryReqDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import org.ticketing_system.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.ticketing_system.biz.orderservice.mq.event.DelayCloseOrderEvent;
import org.ticketing_system.biz.orderservice.mq.event.PayResultCallbackOrderEvent;
import org.ticketing_system.biz.orderservice.mq.produce.DelayCloseOrderSendProduce;
import org.ticketing_system.biz.orderservice.service.OrderItemService;
import org.ticketing_system.biz.orderservice.service.OrderPassengerRelationService;
import org.ticketing_system.biz.orderservice.service.OrderService;
import org.ticketing_system.biz.orderservice.service.orderid.OrderIdGeneratorManager;
import org.ticketing_system.framework.starter.common.enums.DelEnum;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;
import org.ticketing_system.framework.starter.convention.exception.ClientException;
import org.ticketing_system.framework.starter.convention.exception.ServiceException;
import org.ticketing_system.framework.starter.convention.page.PageResponse;
import org.ticketing_system.framework.starter.database.toolkit.PageUtil;
import org.ticketing_system.frameworks.starter.user.core.UserContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单服务接口层实现
 * 
 * @author lin667z
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 订单查询时间范围：最近 N 天 */
    private static final int ORDER_QUERY_DAYS = 30;
    private static final long FIRST_PAGE = 1L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemService orderItemService;
    private final OrderPassengerRelationService orderPassengerRelationService;
    private final RedissonClient redissonClient;
    private final DelayCloseOrderSendProduce delayCloseOrderSendProduce;

    @Override
    public TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn) {
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn);
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        TicketOrderDetailRespDTO result = BeanUtil.convert(orderDO, TicketOrderDetailRespDTO.class);
        LambdaQueryWrapper<OrderItemDO> orderItemQueryWrapper = Wrappers.lambdaQuery(OrderItemDO.class)
                .eq(OrderItemDO::getOrderSn, orderSn);
        List<OrderItemDO> orderItemDOList = orderItemMapper.selectList(orderItemQueryWrapper);
        result.setPassengerDetails(BeanUtil.convert(orderItemDOList, TicketOrderPassengerDetailRespDTO.class));
        return result;
    }

    @AutoOperate(type = TicketOrderDetailRespDTO.class, on = "data.records")
    @Override
    public PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        String userId = UserContext.getUserId();
        Date thirtyDaysAgo = Date.from(LocalDateTime.now().minusDays(ORDER_QUERY_DAYS)
                .atZone(ZoneId.systemDefault()).toInstant());
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getUserId, userId)
                .eq(OrderDO::getDelFlag, DelEnum.NORMAL.code())
                .ge(OrderDO::getCreateTime, thirtyDaysAgo)
                .in(OrderDO::getStatus, buildOrderStatusList(requestParam))
                .orderByDesc(OrderDO::getOrderTime);
        IPage<OrderDO> orderPage = orderMapper.selectPage(PageUtil.convert(requestParam), queryWrapper);
        return PageUtil.convert(orderPage, each -> {
            TicketOrderDetailRespDTO result = BeanUtil.convert(each, TicketOrderDetailRespDTO.class);
            LambdaQueryWrapper<OrderItemDO> orderItemQueryWrapper = Wrappers.lambdaQuery(OrderItemDO.class)
                    .eq(OrderItemDO::getOrderSn, each.getOrderSn());
            List<OrderItemDO> orderItemDOList = orderItemMapper.selectList(orderItemQueryWrapper);
            result.setPassengerDetails(BeanUtil.convert(orderItemDOList, TicketOrderPassengerDetailRespDTO.class));
            return result;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String createTicketOrder(TicketOrderCreateReqDTO requestParam) {
        // 通过基因法将用户 ID 融入到订单号
        String orderSn = OrderIdGeneratorManager.generateId(requestParam.getUserId());
        OrderDO orderDO = OrderDO.builder().orderSn(orderSn)
                .orderTime(requestParam.getOrderTime() != null ? requestParam.getOrderTime() : new Date())
                .departure(requestParam.getDeparture())
                .departureTime(requestParam.getDepartureTime())
                .ridingDate(requestParam.getRidingDate())
                .arrivalTime(requestParam.getArrivalTime())
                .trainNumber(requestParam.getTrainNumber())
                .arrival(requestParam.getArrival())
                .trainId(requestParam.getTrainId())
                .source(requestParam.getSource())
                .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .username(requestParam.getUsername())
                .userId(String.valueOf(requestParam.getUserId()))
                .build();
        orderMapper.insert(orderDO);
        List<TicketOrderItemCreateReqDTO> ticketOrderItems = requestParam.getTicketOrderItems();
        List<OrderItemDO> orderItemDOList = new ArrayList<>();
        List<OrderItemPassengerDO> orderPassengerRelationDOList = new ArrayList<>();
        ticketOrderItems.forEach(each -> {
            OrderItemDO orderItemDO = OrderItemDO.builder()
                    .trainId(requestParam.getTrainId())
                    .seatNumber(each.getSeatNumber())
                    .carriageNumber(each.getCarriageNumber())
                    .realName(each.getRealName())
                    .orderSn(orderSn)
                    .phone(each.getPhone())
                    .seatType(each.getSeatType())
                    .username(requestParam.getUsername()).amount(each.getAmount())
                    .carriageNumber(each.getCarriageNumber())
                    .idCard(each.getIdCard())
                    .ticketType(each.getTicketType())
                    .idType(each.getIdType())
                    .userId(String.valueOf(requestParam.getUserId()))
                    .status(0)
                    .build();
            orderItemDOList.add(orderItemDO);
            OrderItemPassengerDO orderPassengerRelationDO = OrderItemPassengerDO.builder()
                    .idType(each.getIdType())
                    .idCard(each.getIdCard())
                    .orderSn(orderSn)
                    .build();
            orderPassengerRelationDOList.add(orderPassengerRelationDO);
        });
        orderItemService.saveBatch(orderItemDOList);
        orderPassengerRelationService.saveBatch(orderPassengerRelationDOList);
        try {
            // 发送 RocketMQ 延时消息，指定时间后取消订单
            DelayCloseOrderEvent delayCloseOrderEvent = DelayCloseOrderEvent.builder()
                    .trainId(String.valueOf(requestParam.getTrainId()))
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderSn(orderSn)
                    .trainPurchaseTicketResults(requestParam.getTicketOrderItems())
                    .build();
            SendResult sendResult = delayCloseOrderSendProduce.sendMessage(delayCloseOrderEvent);
            if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
                throw new ServiceException("投递延迟关闭订单消息队列失败");
            }
        } catch (Throwable ex) {
            log.error("延迟关闭订单消息队列发送错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
        return orderSn;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean closeTickOrder(CancelTicketOrderReqDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn)
                .select(OrderDO::getStatus);
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        if (Objects.isNull(orderDO) || orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            return false;
        }
        // 原则上订单关闭和订单取消这两个方法可以复用，为了区分未来考虑到的场景，这里对方法进行拆分但复用逻辑
        return cancelTickOrder(requestParam);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn);
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        if (orderDO == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        } else if (orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock(StrBuilder.create("order:canal:order_sn_").append(orderSn).toString());
        if (!lock.tryLock()) {
            throw new ClientException(OrderCanalErrorCodeEnum.ORDER_CANAL_REPETITION_ERROR);
        }
        try {
            OrderDO updateOrderDO = new OrderDO();
            updateOrderDO.setStatus(OrderStatusEnum.CLOSED.getStatus());
            LambdaUpdateWrapper<OrderDO> updateWrapper = Wrappers.lambdaUpdate(OrderDO.class)
                    .eq(OrderDO::getOrderSn, orderSn);
            int updateResult = orderMapper.update(updateOrderDO, updateWrapper);
            if (updateResult <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_ERROR);
            }
            OrderItemDO updateOrderItemDO = new OrderItemDO();
            updateOrderItemDO.setStatus(OrderItemStatusEnum.CLOSED.getStatus());
            LambdaUpdateWrapper<OrderItemDO> updateItemWrapper = Wrappers.lambdaUpdate(OrderItemDO.class)
                    .eq(OrderItemDO::getOrderSn, orderSn);
            int updateItemResult = orderItemMapper.update(updateOrderItemDO, updateItemWrapper);
            if (updateItemResult <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_ERROR);
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public void statusReversal(OrderStatusReversalDTO requestParam) {
        LambdaQueryWrapper<OrderDO> queryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, requestParam.getOrderSn());
        OrderDO orderDO = orderMapper.selectOne(queryWrapper);
        if (orderDO == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        } else if (orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock(
                StrBuilder.create("order:status-reversal:order_sn_").append(requestParam.getOrderSn()).toString());
        if (!lock.tryLock()) {
            log.warn("订单重复修改状态，状态反转请求参数：{}", JSON.toJSONString(requestParam));
        }
        try {
            OrderDO updateOrderDO = new OrderDO();
            updateOrderDO.setStatus(requestParam.getOrderStatus());
            LambdaUpdateWrapper<OrderDO> updateWrapper = Wrappers.lambdaUpdate(OrderDO.class)
                    .eq(OrderDO::getOrderSn, requestParam.getOrderSn());
            int updateResult = orderMapper.update(updateOrderDO, updateWrapper);
            if (updateResult <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
            OrderItemDO orderItemDO = new OrderItemDO();
            orderItemDO.setStatus(requestParam.getOrderItemStatus());
            LambdaUpdateWrapper<OrderItemDO> orderItemUpdateWrapper = Wrappers.lambdaUpdate(OrderItemDO.class)
                    .eq(OrderItemDO::getOrderSn, requestParam.getOrderSn());
            int orderItemUpdateResult = orderItemMapper.update(orderItemDO, orderItemUpdateWrapper);
            if (orderItemUpdateResult <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void payCallbackOrder(PayResultCallbackOrderEvent requestParam) {
        OrderDO updateOrderDO = new OrderDO();
        updateOrderDO.setPayTime(requestParam.getGmtPayment());
        updateOrderDO.setPayType(requestParam.getChannel());
        LambdaUpdateWrapper<OrderDO> updateWrapper = Wrappers.lambdaUpdate(OrderDO.class)
                .eq(OrderDO::getOrderSn, requestParam.getOrderSn());
        int updateResult = orderMapper.update(updateOrderDO, updateWrapper);
        if (updateResult <= 0) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
        }
    }

    @Override
    public PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderSelfPageQueryReqDTO requestParam) {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new ClientException("未获取到当前登录用户");
        }
        List<OrderDO> orders = querySelfOrdersByUserId(userId);
        if (orders.isEmpty()) {
            return buildSelfOrderPage(List.of(), requestParam.getCount(), 0);
        }
        LocalDateTime queryStartTime = resolveSelfOrderStartTime(requestParam.getDate(), orders);
        if (queryStartTime == null) {
            return buildSelfOrderPage(List.of(), requestParam.getCount(), 0);
        }
        Date startTime = toDate(queryStartTime.toLocalDate().atStartOfDay());
        Date endTime = toDate(queryStartTime.toLocalDate().atTime(LocalTime.MAX));
        List<OrderDO> matchedOrders = orders.stream()
                .filter(each -> each.getOrderTime() != null)
                .filter(each -> !each.getOrderTime().before(startTime) && !each.getOrderTime().after(endTime))
                .toList();
        Set<String> orderSnSet = matchedOrders.stream()
                .map(OrderDO::getOrderSn)
                .collect(Collectors.toSet());
        Map<String, OrderDO> orderMap = matchedOrders.stream()
                .collect(Collectors.toMap(OrderDO::getOrderSn, each -> each, (left, right) -> left, LinkedHashMap::new));
        List<OrderItemDO> orderItems = querySelfOrderItems(userId, orderSnSet);
        List<TicketOrderDetailSelfRespDTO> allRecords = orderItems.stream()
                .filter(each -> orderMap.containsKey(each.getOrderSn()))
                .map(each -> buildSelfOrderDetail(each, orderMap.get(each.getOrderSn())))
                .toList();
        List<TicketOrderDetailSelfRespDTO> limitedRecords = limitSelfOrderRecords(allRecords, requestParam.getCount());
        return buildSelfOrderPage(limitedRecords, requestParam.getCount(), allRecords.size());
    }

    private List<OrderDO> querySelfOrdersByUserId(String userId) {
        LambdaQueryWrapper<OrderDO> orderQueryWrapper = Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getUserId, userId)
                .eq(OrderDO::getDelFlag, DelEnum.NORMAL.code())
                .orderByDesc(OrderDO::getOrderTime);
        return orderMapper.selectList(orderQueryWrapper);
    }

    private List<OrderItemDO> querySelfOrderItems(String userId, Set<String> orderSnSet) {
        if (orderSnSet.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<OrderItemDO> orderItemQueryWrapper = Wrappers.lambdaQuery(OrderItemDO.class)
                .eq(OrderItemDO::getUserId, userId)
                .in(OrderItemDO::getOrderSn, orderSnSet)
                .orderByDesc(OrderItemDO::getCreateTime);
        return orderItemMapper.selectList(orderItemQueryWrapper);
    }

    private LocalDateTime resolveSelfOrderStartTime(String date, List<OrderDO> orders) {
        if (date != null && !date.isBlank()) {
            try {
                return java.time.LocalDate.parse(date).atStartOfDay();
            } catch (DateTimeParseException ex) {
                throw new ClientException("订单查询日期格式错误，请使用 yyyy-MM-dd");
            }
        }
        if (orders.isEmpty()) {
            return null;
        }
        Date latestOrderTime = orders.get(0).getOrderTime();
        return latestOrderTime == null ? null : latestOrderTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private TicketOrderDetailSelfRespDTO buildSelfOrderDetail(OrderItemDO orderItemDO, OrderDO orderDO) {
        TicketOrderDetailSelfRespDTO actualResult = BeanUtil.convert(orderDO, TicketOrderDetailSelfRespDTO.class);
        BeanUtil.convertIgnoreNullAndBlank(orderItemDO, actualResult);
        return actualResult;
    }

    private List<TicketOrderDetailSelfRespDTO> limitSelfOrderRecords(List<TicketOrderDetailSelfRespDTO> records, Long count) {
        if (count == null || count < 1 || records.size() <= count) {
            return records;
        }
        int limit = (int) Math.min(count, (long) Integer.MAX_VALUE);
        return records.subList(0, limit);
    }

    private PageResponse<TicketOrderDetailSelfRespDTO> buildSelfOrderPage(List<TicketOrderDetailSelfRespDTO> records, Long count, int total) {
        long size = count == null || count < 1 ? records.size() : count;
        return PageResponse.<TicketOrderDetailSelfRespDTO>builder()
                .current(FIRST_PAGE)
                .size(size)
                .total((long) total)
                .records(records)
                .build();
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private List<Integer> buildOrderStatusList(TicketOrderPageQueryReqDTO requestParam) {
        List<Integer> result = new ArrayList<>();
        switch (requestParam.getStatusType()) {
            case 0 -> result = ListUtil.of(
                    OrderStatusEnum.PENDING_PAYMENT.getStatus());
            case 1 -> result = ListUtil.of(
                    OrderStatusEnum.ALREADY_PAID.getStatus(),
                    OrderStatusEnum.PARTIAL_REFUND.getStatus(),
                    OrderStatusEnum.FULL_REFUND.getStatus());
            case 2 -> result = ListUtil.of(
                    OrderStatusEnum.COMPLETED.getStatus());
        }
        return result;
    }
}
