package org.ticketing_system.biz.payservice.service;
import org.ticketing_system.biz.payservice.dto.RefundReqDTO;
import org.ticketing_system.biz.payservice.dto.RefundRespDTO;

/**
 * 退款接口层
 * @author lin667z
 */
public interface RefundService {

    /**
     * 公共退款接口
     *
     * @param requestParam 退款请求参数
     * @return 退款返回详情
     */
    RefundRespDTO commonRefund(RefundReqDTO requestParam);
}


