package org.ticketing_system.biz.ticketservice.job;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.ticketservice.common.constant.TicketingSystemConstant;
import org.ticketing_system.biz.ticketservice.dao.entity.TrainDO;
import org.ticketing_system.biz.ticketservice.dao.entity.TrainStationDO;
import org.ticketing_system.biz.ticketservice.dao.mapper.TrainStationMapper;
import org.ticketing_system.biz.ticketservice.job.base.AbstractTrainStationJobHandlerTemplate;
import org.ticketing_system.framework.starter.cache.DistributedCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.ticketing_system.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_STOPOVER_DETAIL;

/**
 * 列车路线信息定时任务
 * 已通过运行时判断缓存不存在实时读取数据库获取完成，该定时任务不在主流程中
 * @author lin667z
 */
@Deprecated
@RestController
@RequiredArgsConstructor
public class TrainStationJobHandler extends AbstractTrainStationJobHandlerTemplate {

    private final TrainStationMapper trainStationMapper;
    private final DistributedCache distributedCache;

    @XxlJob(value = "trainStationJobHandler")
    @GetMapping("/api/ticket-service/train-station/job/cache-init/execute")
    @Override
    public void execute() {
        super.execute();
    }

    @Override
    protected void actualExecute(List<TrainDO> trainDOPageRecords) {
        for (TrainDO each : trainDOPageRecords) {
            LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                    .eq(TrainStationDO::getTrainId, each.getId());
            List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
            distributedCache.put(
                    TRAIN_STATION_STOPOVER_DETAIL + each.getId(),
                    JSON.toJSONString(trainStationDOList),
                    TicketingSystemConstant.ADVANCE_TICKET_DAY,
                    TimeUnit.DAYS
            );
        }
    }
}


