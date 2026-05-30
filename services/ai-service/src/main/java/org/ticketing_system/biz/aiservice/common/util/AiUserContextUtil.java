package org.ticketing_system.biz.aiservice.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.framework.starter.convention.exception.ClientException;
import org.ticketing_system.frameworks.starter.user.core.UserContext;

/**
 * AI 用户上下文工具类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiUserContextUtil {

    /**
     * 获取当前认证用户
     */
    public static AiAuthenticatedUserContext getAuthenticatedUser() {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (userId == null || userId.isBlank() || username == null || username.isBlank()) {
            throw new ClientException("用户未登录");
        }
        try {
            return AiAuthenticatedUserContext.builder()
                    .userId(Long.valueOf(userId))
                    .username(username)
                    .realName(UserContext.getRealName())
                    .token(UserContext.getToken())
                    .build();
        } catch (NumberFormatException ex) {
            throw new ClientException("用户 ID 格式错误");
        }
    }
}
