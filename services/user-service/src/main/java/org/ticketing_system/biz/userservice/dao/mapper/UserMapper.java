package org.ticketing_system.biz.userservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.ticketing_system.biz.userservice.dao.entity.UserDO;

/**
 * 用户信息持久层
 * @author lin667z
 */
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 注销用户
     *
     * @param userDO 注销用户入参
     */
    void deletionUser(UserDO userDO);
}


