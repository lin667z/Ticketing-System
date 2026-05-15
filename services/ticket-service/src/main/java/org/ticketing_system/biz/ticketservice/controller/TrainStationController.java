package org.ticketing_system.biz.ticketservice.controller;

import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.ticketservice.dto.resp.TrainStationQueryRespDTO;
import org.ticketing_system.biz.ticketservice.service.TrainStationService;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.ticketing_system.framework.starter.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 列车站点控制层
 * @author lin667z
 */
@RestController
@RequiredArgsConstructor
public class TrainStationController {

    private final TrainStationService trainStationService;

    /**
     * 根据列车 ID 查询站点信息
     */
    @GetMapping("/api/ticket-service/train-station/query")
    public Result<List<TrainStationQueryRespDTO>> listTrainStationQuery(String trainId) {
        return Results.success(trainStationService.listTrainStationQuery(trainId));
    }
}


