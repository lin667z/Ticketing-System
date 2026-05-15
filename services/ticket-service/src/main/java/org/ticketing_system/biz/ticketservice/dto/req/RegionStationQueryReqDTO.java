package org.ticketing_system.biz.ticketservice.dto.req;

import lombok.Data;

/**
 * 地区&站点查询请求入参
 * @author lin667z
 */
@Data
public class RegionStationQueryReqDTO {

    /**
     * 查询方式
     */
    private Integer queryType;

    /**
     * 名称
     */
    private String name;
}


