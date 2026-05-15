package org.ticketing_system.biz.userservice.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户注册错误码枚举
 * @author lin667z
 */
@AllArgsConstructor
public enum VerifyStatusEnum {

    /**
     * 未审核
     */
    UNREVIEWED(0),

    /**
     * 已审核
     */
    REVIEWED(1);

    @Getter
    private final int code;
}


