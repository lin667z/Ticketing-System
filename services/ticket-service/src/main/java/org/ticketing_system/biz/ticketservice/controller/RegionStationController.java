package org.ticketing_system.biz.ticketservice.controller;

import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.ticketservice.dto.req.RegionStationQueryReqDTO;
import org.ticketing_system.biz.ticketservice.dto.resp.RegionStationQueryRespDTO;
import org.ticketing_system.biz.ticketservice.dto.resp.StationQueryRespDTO;
import org.ticketing_system.biz.ticketservice.service.RegionStationService;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.ticketing_system.framework.starter.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地区以及车站查询控制层
 * @author lin667z
 */
@RestController
@RequiredArgsConstructor
public class RegionStationController {

    private final RegionStationService regionStationService;

    /**
     * 查询车站&城市站点集合信息
     */
    @GetMapping("/api/ticket-service/region-station/query")
    public Result<List<RegionStationQueryRespDTO>> listRegionStation(RegionStationQueryReqDTO requestParam) {
        return Results.success(regionStationService.listRegionStation(requestParam));
    }

    /**
     * 查询车站站点集合信息
     */
    @GetMapping("/api/ticket-service/station/all")
    public Result<List<StationQueryRespDTO>> listAllStation() {
        return Results.success(regionStationService.listAllStation());
    }
}


