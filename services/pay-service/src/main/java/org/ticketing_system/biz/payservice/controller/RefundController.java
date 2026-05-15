package org.ticketing_system.biz.payservice.controller;

import lombok.RequiredArgsConstructor;
import org.ticketing_system.biz.payservice.dto.RefundReqDTO;
import org.ticketing_system.biz.payservice.dto.RefundRespDTO;
import org.ticketing_system.biz.payservice.service.RefundService;
import org.ticketing_system.framework.starter.convention.result.Result;
import org.ticketing_system.framework.starter.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款控制层
 * @author lin667z
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * 公共退款接口
     */
    @PostMapping("/api/pay-service/common/refund")
    public Result<RefundRespDTO> commonRefund(@RequestBody RefundReqDTO requestParam) {
        return Results.success(refundService.commonRefund(requestParam));
    }
}


