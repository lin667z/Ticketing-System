package org.ticketing_system.biz.userservice.service.handler.filter.user;

import org.ticketing_system.biz.userservice.common.enums.UserChainMarkEnum;
import org.ticketing_system.biz.userservice.dto.req.UserRegisterReqDTO;
import org.ticketing_system.framework.starter.designpattern.chain.AbstractChainHandler;

/**
 * 用户注册责任链过滤器
 * @author lin667z
 */
public interface UserRegisterCreateChainFilter<T extends UserRegisterReqDTO> extends AbstractChainHandler<UserRegisterReqDTO> {

    @Override
    default String mark() {
        return UserChainMarkEnum.USER_REGISTER_FILTER.name();
    }
}


