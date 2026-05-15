package org.ticketing_system.biz.orderservice.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Objects;

/**
 * 支付渠道枚举
 * @author lin667z
 */
@RequiredArgsConstructor
public enum PayChannelEnum {

    /**
     * 支付宝
     */
    ALI_PAY(0, "ALI_PAY", "支付宝");

    @Getter
    private final Integer code;

    @Getter
    private final String name;

    @Getter
    private final String value;
}


