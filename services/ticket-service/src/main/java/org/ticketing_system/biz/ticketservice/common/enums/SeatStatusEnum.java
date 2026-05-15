package org.ticketing_system.biz.ticketservice.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 座位状态枚举
 * @author lin667z
 */
@RequiredArgsConstructor
public enum SeatStatusEnum {

    /**
     * 可售
     */
    AVAILABLE(0),

    /**
     * 锁定
     */
    LOCKED(1),

    /**
     * 已售
     */
    SOLD(2);

    @Getter
    private final Integer code;
}


