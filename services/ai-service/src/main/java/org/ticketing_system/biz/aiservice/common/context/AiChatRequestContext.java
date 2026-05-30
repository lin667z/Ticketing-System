package org.ticketing_system.biz.aiservice.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.model.AiUserProfile;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;

/**
 * 单次 AI 聊天请求的内部上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequestContext {

    /**
     * 聊天会话 ID
     */
    private Long sessionId;

    /**
     * Whether this request created the session in the current turn.
     */
    private boolean newSession;

    /**
     * Best-effort turn count known before orchestration, when available.
     */
    private Integer knownTurnCount;

    /**
     * 捕获的已认证用户（需在流式响应中显式传递以避免作用域过期）
     */
    private AiAuthenticatedUserContext authenticatedUser;

    /**
     * 此请求的用户画像快照
     */
    private AiUserProfile userProfile;

    /**
     * 当前用户消息
     */
    private AiChatMessage currentMessage;

    /**
     * 当前会话的双轨上下文快照
     */
    private AiSessionContext sessionContext;

    /**
     * 是否启用工具调用
     */
    private boolean enableTools;

    /**
     * 获取用户 ID
     */
    public Long getUserId() {
        return authenticatedUser == null ? null : authenticatedUser.getUserId();
    }
}
