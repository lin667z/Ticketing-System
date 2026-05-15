package org.ticketing_system.biz.userservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * 用户手机号实体对象
 * @author lin667z
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_user_phone")
public class UserPhoneDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 注销时间戳
     */
    private Long deletionTime;
}


