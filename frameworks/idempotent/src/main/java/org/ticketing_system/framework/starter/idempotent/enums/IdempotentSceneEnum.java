package org.ticketing_system.framework.starter.idempotent.enums;

/**
 * 幂等验证场景枚举
 * @author lin667z
 */
public enum IdempotentSceneEnum {
    
    /**
     * 基于 RestAPI 场景验证
     */
    RESTAPI,
    
    /**
     * 基于 MQ 场景验证
     */
    MQ
}


