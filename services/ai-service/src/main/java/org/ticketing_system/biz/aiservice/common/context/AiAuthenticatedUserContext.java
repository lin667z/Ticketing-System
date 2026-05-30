package org.ticketing_system.biz.aiservice.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 认证用户上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuthenticatedUserContext {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 访问令牌
     */
    private String token;
}
