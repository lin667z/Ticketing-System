package org.ticketing_system.biz.userservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.ticketing_system.biz.userservice.dao.entity.UserMailDO;

/**
 * 用户邮箱表持久层
 * @author lin667z
 */
public interface UserMailMapper extends BaseMapper<UserMailDO> {

    /**
     * 注销用户
     *
     * @param userMailDO 注销用户入参
     */
    void deletionUser(UserMailDO userMailDO);
}


