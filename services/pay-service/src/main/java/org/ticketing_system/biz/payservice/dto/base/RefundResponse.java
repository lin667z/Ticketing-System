package org.ticketing_system.biz.payservice.dto.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退款返回
 * @author lin667z
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class RefundResponse {

    /**
     * 退款状态
     */
    private Integer status;

    /**
     * 第三方交易凭证
     */
    private String tradeNo;
}


