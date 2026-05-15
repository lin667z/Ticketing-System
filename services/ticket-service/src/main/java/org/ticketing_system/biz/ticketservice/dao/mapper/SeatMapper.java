package org.ticketing_system.biz.ticketservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.ticketing_system.biz.ticketservice.dao.entity.SeatDO;
import org.ticketing_system.biz.ticketservice.dto.domain.SeatTypeCountDTO;

import java.util.List;

/**
 * 座位持久层
 * @author lin667z
 */
public interface SeatMapper extends BaseMapper<SeatDO> {

    /**
     * 获取列车车厢余票集合
     */
    List<Integer> listSeatRemainingTicket(@Param("seatDO") SeatDO seatDO, @Param("trainCarriageList") List<String> trainCarriageList);

    /**
     * 获取列车 startStation 到 endStation 区间可用座位数量
     */
    List<SeatTypeCountDTO> listSeatTypeCount(@Param("trainId") Long trainId, @Param("startStation") String startStation, @Param("endStation") String endStation, @Param("seatTypes") List<Integer> seatTypes);
}


