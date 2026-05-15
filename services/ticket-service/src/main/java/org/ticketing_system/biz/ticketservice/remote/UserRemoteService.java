package org.ticketing_system.biz.ticketservice.remote;

import org.ticketing_system.biz.ticketservice.remote.dto.PassengerRespDTO;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户远程服务调用
 * @author lin667z
 */
@FeignClient(value = "ticketing-system-user${unique-name:}-service")
public interface UserRemoteService {

    /**
     * 根据乘车人 ID 集合查询乘车人列表
     */
    @GetMapping("/api/user-service/inner/passenger/actual/query/ids")
    Result<List<PassengerRespDTO>> listPassengerQueryByIds(@RequestParam("username") String username, @RequestParam("ids") List<String> ids);
}


