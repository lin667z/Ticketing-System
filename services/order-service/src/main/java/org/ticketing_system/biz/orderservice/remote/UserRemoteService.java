package org.ticketing_system.biz.orderservice.remote;

import jakarta.validation.constraints.NotEmpty;
import org.ticketing_system.biz.orderservice.remote.dto.UserQueryActualRespDTO;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户远程服务调用
 * @author lin667z
 */
@FeignClient(value = "ticketing-system-user${unique-name:}-service")
public interface UserRemoteService {

    /**
     * 根据乘车人 ID 集合查询乘车人列表
     */
    @GetMapping("/api/user-service/actual/query")
    Result<UserQueryActualRespDTO> queryActualUserByUsername(@RequestParam("username") @NotEmpty String username);
}


