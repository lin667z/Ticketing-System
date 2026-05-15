package org.ticketing_system.biz.ticketservice.dto.req;

import lombok.Data;
import org.ticketing_system.framework.starter.convention.page.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 车票分页查询请求参数
 * @author lin667z
 */
@Data
public class TicketPageQueryReqDTO extends PageRequest {

    /**
     * 出发地 Code
     */
    private String fromStation;

    /**
     * 目的地 Code
     */
    private String toStation;

    /**
     * 出发日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date departureDate;

    /**
     * 出发站点
     */
    private String departure;

    /**
     * 到达站点
     */
    private String arrival;
}


