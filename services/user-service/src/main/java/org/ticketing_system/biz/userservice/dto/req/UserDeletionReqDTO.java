package org.ticketing_system.biz.userservice.dto.req;

import lombok.Data;

/**
 * 用户注销请求参数
 * @author lin667z
 */
@Data
public class UserDeletionReqDTO {

    /**
     * 用户名
     */
    private String username;
}


