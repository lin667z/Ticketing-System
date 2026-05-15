package org.ticketing_system.biz.ticketservice;

import cn.hutool.core.map.MapUtil;
import org.junit.jupiter.api.Test;
import org.ticketing_system.biz.ticketservice.dao.entity.CarriageDO;
import org.ticketing_system.biz.ticketservice.dao.mapper.CarriageMapper;
import org.ticketing_system.framework.starter.common.enums.DelEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class TrainCarriageTests {

    @Autowired
    private CarriageMapper carriageMapper;

    /**
     * 列车 ID
     * 车厢号 -> 车厢总数
     * 车厢类型 -> 车厢类型集合
     * 座位数 -> 车厢类型对应的座位数
     */
    @Test
    void testInitData() {
        long trainId = 4L;
        int num = 17;
        // Map<Integer, Map<Integer, Integer>> typeCountMap = MapUtil.of(3, MapUtil.of(5, 24));
        // typeCountMap.put(4, MapUtil.of(11, 32));
        // typeCountMap.put(5, MapUtil.of(17, 36));
        Map<Integer, Map<Integer, Integer>> typeCountMap = MapUtil.of(6, MapUtil.of(5, 24));
        typeCountMap.put(7, MapUtil.of(11, 32));
        typeCountMap.put(8, MapUtil.of(17, 36));
        List<CarriageDO> carriageDOList = new ArrayList<>();
        for (int i = 1; i < num; i++) {
            CarriageDO carriageDO = new CarriageDO();
            carriageDO.setTrainId(trainId);
            String carriageNumber = String.valueOf(i);
            if (i < 10) {
                carriageNumber = "0" + carriageNumber;
            }
            carriageDO.setCarriageNumber(carriageNumber);
            AtomicInteger atomicInteger = new AtomicInteger(i);
            AtomicInteger carriageType = new AtomicInteger();
            AtomicInteger seatCount = new AtomicInteger();
            AtomicBoolean flag = new AtomicBoolean(Boolean.TRUE);
            typeCountMap.forEach((key, val) -> val.forEach((key1, val1) -> {
                if (key1 > atomicInteger.get() && flag.get()) {
                    carriageType.set(key);
                    seatCount.set(val1);
                    flag.set(Boolean.FALSE);
                }
            }));
            carriageDO.setCarriageType(carriageType.get());
            carriageDO.setSeatCount(seatCount.get());
            carriageDO.setCreateTime(new Date());
            carriageDO.setUpdateTime(new Date());
            carriageDO.setDelFlag(DelEnum.NORMAL.code());
            carriageDOList.add(carriageDO);
        }
        carriageDOList.forEach(carriageMapper::insert);
    }
}


