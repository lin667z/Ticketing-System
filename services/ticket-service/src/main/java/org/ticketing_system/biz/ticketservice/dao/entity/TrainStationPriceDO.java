package org.ticketing_system.biz.ticketservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * 列车站点价格实体
 * @author lin667z
 */
@Data
@TableName("t_train_station_price")
public class TrainStationPriceDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 车次id
     */
    private Long trainId;

    /**
     * 座位类型
     */
    private Integer seatType;

    /**
     * 出发站点
     */
    private String departure;

    /**
     * 到达站点
     */
    private String arrival;

    /**
     * 车票价格
     */
    private Integer price;
}


