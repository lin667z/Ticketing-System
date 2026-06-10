package org.ticketing_system.biz.aiservice.service;

import org.ticketing_system.biz.aiservice.dto.req.AiMemoryReqDTO;
import org.ticketing_system.biz.aiservice.dto.resp.AiMemoryRespDTO;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.dto.domain.AiUserProfile;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI 用户画像服务接口，支持异步获取用户信息与长期记忆管理
 */
public interface AiUserProfileService {

    /**
     * 验证并异步构建用户画像
     *
     * @param userContext    已认证用户上下文
     * @param requestContext 请求上下文
     * @return 用户画像（Mono 包装）
     */
    Mono<AiUserProfile> validateAndBuildProfile(AiAuthenticatedUserContext userContext, AiChatRequestContext requestContext);

    /**
     * 异步构建用户画像（不含身份验证）
     */
    Mono<AiUserProfile> buildProfile(AiAuthenticatedUserContext userContext, AiChatRequestContext requestContext);

    /**
     * 获取长期记忆列表
     */
    List<AiMemoryRespDTO> listLongTermMemories(AiAuthenticatedUserContext userContext);

    /**
     * 创建长期记忆
     */
    AiMemoryRespDTO createLongTermMemory(AiAuthenticatedUserContext userContext, AiMemoryReqDTO requestParam);

    /**
     * 更新长期记忆
     */
    AiMemoryRespDTO updateLongTermMemory(AiAuthenticatedUserContext userContext, Long id, AiMemoryReqDTO requestParam);

    /**
     * 删除长期记忆
     */
    void deleteLongTermMemory(AiAuthenticatedUserContext userContext, Long id);
}
