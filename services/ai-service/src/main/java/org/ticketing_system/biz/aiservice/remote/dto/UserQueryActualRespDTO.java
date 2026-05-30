package org.ticketing_system.biz.aiservice.remote.dto;

import lombok.Data;

/**
 * 用户真实信息查询响应 DTO。
 */
@Data
public class UserQueryActualRespDTO {

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 地区 */
    private String region;

    /** 证件类型 */
    private Integer idType;

    /** 证件号 */
    private String idCard;

    /** 手机号 */
    private String phone;

    /** 固定电话 */
    private String telephone;

    /** 邮箱 */
    private String mail;

    /** 用户类型 */
    private Integer userType;

    /** 认证状态 */
    private Integer verifyStatus;

    /** 邮编 */
    private String postCode;

    /** 地址 */
    private String address;
}
