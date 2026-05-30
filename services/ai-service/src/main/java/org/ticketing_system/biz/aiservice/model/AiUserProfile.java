package org.ticketing_system.biz.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 场景下的用户画像模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserProfile {

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 证件类型
     */
    private Integer idType;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 认证状态
     */
    private Integer verifyStatus;

    /**
     * 用户标签
     */
    private String userTag;

    /**
     * 用户偏好
     */
    private String preferences;

    /**
     * 记忆内容列表
     */
    private List<String> memoryContents;

    /**
     * Whether this profile was built from authenticated request context because user-service was temporarily unavailable.
     */
    private Boolean degraded;
}
