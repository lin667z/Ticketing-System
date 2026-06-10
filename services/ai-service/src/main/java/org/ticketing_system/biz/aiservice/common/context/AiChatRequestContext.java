package org.ticketing_system.biz.aiservice.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventCollector;
import org.ticketing_system.biz.aiservice.memory.execution.ExecutionContext;
import org.ticketing_system.biz.aiservice.dto.domain.AiChatMessage;
import org.ticketing_system.biz.aiservice.dto.domain.AiUserProfile;
import org.ticketing_system.biz.aiservice.memory.MemoryContext;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;

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
     * 当前轮次是否创建了会话
     */
    private boolean newSession;

    /**
     * 编排前已知的轮次计数（如有）
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
     * 当前会话的工作记忆快照（L1，统一会话状态）
     */
    private WorkingMemoryState workingMemory;

    /**
     * 本轮组装的记忆上下文（L1 + L4 偏好 + L5 规则片段）
     */
    private MemoryContext memoryContext;

    private ExecutionContext executionContext;

    private SessionEventCollector eventCollector;

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
