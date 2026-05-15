package org.ticketing_system.biz.ticketservice.dto.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站点路线实体
 * @author lin667z
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteDTO {

    /**
     * 出发站点
     */
    private String startStation;

    /**
     * 目的站点
     */
    private String endStation;
}


