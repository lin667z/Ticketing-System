package org.ticketing_system.biz.ticketservice;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.ticketing_system.biz.ticketservice.dao.entity.StationDO;
import org.ticketing_system.biz.ticketservice.dao.entity.TrainStationDO;
import org.ticketing_system.biz.ticketservice.dao.mapper.StationMapper;
import org.ticketing_system.biz.ticketservice.dao.mapper.TrainStationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class TrainStationTests {

    @Autowired
    private StationMapper stationMapper;

    @Autowired
    private TrainStationMapper trainStationMapper;

    @Test
    void testInitData() {
        long trainId = 3L;
        Map<String, Map<String, Object>> dataMap = new LinkedHashMap<>();
        dataMap.put("北京", buildInnerDataMap("德州", "2023-06-01 19:16:00", "2023-06-01 19:16:00", null));
        dataMap.put("德州", buildInnerDataMap("南京", "2023-06-01 22:19:00", "2023-06-01 22:21:00", 2));
        dataMap.put("南京", buildInnerDataMap("嘉兴", "2023-06-02 04:46:00", "2023-06-02 04:52:00", 6));
        dataMap.put("嘉兴", buildInnerDataMap("海宁", "2023-06-02 07:56:00", "2023-06-02 07:58:00", 2));
        dataMap.put("海宁", buildInnerDataMap("杭州", "2023-06-02 08:14:00", "2023-06-02 08:16:00", 2));
        dataMap.put("杭州", buildInnerDataMap(null, "2023-06-02 09:00:00", "2023-06-02 09:00:00", null));
        AtomicInteger atomicInteger = new AtomicInteger(1);
        List<TrainStationDO> trainStationDOList = new ArrayList<>();
        dataMap.forEach((key, val) -> {
            TrainStationDO trainStationDO = new TrainStationDO();
            trainStationDO.setTrainId(trainId);
            String sequence = String.valueOf(atomicInteger.get());
            if (atomicInteger.getAndIncrement() > 10) {
                sequence = "0" + sequence;
            }
            trainStationDO.setSequence(sequence);
            trainStationDO.setDeparture(key);
            StationDO startRegionDO = stationMapper.selectOne(Wrappers.lambdaQuery(StationDO.class).eq(StationDO::getName, key));
            trainStationDO.setStartRegion(startRegionDO.getRegionName());
            trainStationDO.setStationId(startRegionDO.getId());
            Object arrival = val.get("arrival");
            if (arrival != null) {
                StationDO endRegionDO = stationMapper.selectOne(Wrappers.lambdaQuery(StationDO.class).eq(StationDO::getName, arrival.toString()));
                trainStationDO.setEndRegion(endRegionDO.getRegionName());
                trainStationDO.setArrival(arrival.toString());
            }
            trainStationDO.setArrivalTime(DateUtil.parse(val.get("arrival_time").toString()));
            trainStationDO.setDepartureTime(DateUtil.parse(val.get("departure_time").toString()));
            Object stopoverTime = val.get("stopover_time");
            if (stopoverTime != null) {
                trainStationDO.setStopoverTime(Integer.parseInt(stopoverTime.toString()));
            }
            trainStationDO.setCreateTime(new Date());
            trainStationDO.setUpdateTime(new Date());
            trainStationDO.setDelFlag(0);
            trainStationDOList.add(trainStationDO);
        });
        trainStationDOList.forEach(trainStationMapper::insert);
    }

    private Map<String, Object> buildInnerDataMap(String arrival, String arrivalTime, String departureTime, Integer stopoverTime) {
        HashMap<String, Object> innerDataMap = new HashMap<>();
        if (StrUtil.isNotBlank(arrival)) {
            innerDataMap.put("arrival", arrival);
        }
        if (StrUtil.isNotBlank(arrivalTime)) {
            innerDataMap.put("arrival_time", arrivalTime);
        }
        if (StrUtil.isNotBlank(departureTime)) {
            innerDataMap.put("departure_time", departureTime);
        }
        if (stopoverTime != null) {
            innerDataMap.put("stopover_time", stopoverTime);
        }
        return innerDataMap;
    }
}


