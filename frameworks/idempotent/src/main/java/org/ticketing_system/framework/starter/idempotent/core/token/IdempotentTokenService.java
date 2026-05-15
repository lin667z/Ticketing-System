package org.ticketing_system.framework.starter.idempotent.core.token;

import org.ticketing_system.framework.starter.idempotent.core.IdempotentExecuteHandler;

/**
 * Token 实现幂等接口
 * @author lin667z
 */
public interface IdempotentTokenService extends IdempotentExecuteHandler {

    /**
     * 创建幂等验证Token
     */
    String createToken();
}


