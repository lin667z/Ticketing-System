package org.ticketing_system.biz.userservice.toolkit;

import static org.ticketing_system.biz.userservice.common.constant.TicketingystemConstant.USER_REGISTER_REUSE_SHARDING_COUNT;
/**
 * 用户名可复用工具类
 * @author lin667z
 */
public final class UserReuseUtil {

    /**
     * 计算分片位置
     */
    public static int hashShardingIdx(String username) {
        return Math.abs(username.hashCode() % USER_REGISTER_REUSE_SHARDING_COUNT);
    }
}


