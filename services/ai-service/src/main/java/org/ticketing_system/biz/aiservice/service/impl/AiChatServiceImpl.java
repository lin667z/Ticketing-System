package org.ticketing_system.biz.aiservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.dao.entity.AiSessionDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiSessionMapper;
import org.ticketing_system.biz.aiservice.dto.req.AiChatReqDTO;
import org.ticketing_system.biz.aiservice.common.util.AiUserContextUtil;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import org.ticketing_system.biz.aiservice.service.AiChatService;
import org.ticketing_system.biz.aiservice.service.AiOrchestratorService;
import org.ticketing_system.framework.starter.convention.exception.ClientException;
import reactor.core.publisher.Flux;

/**
 * AI 聊天服务实现类，负责身份验证、会话管理并委托编排服务执行核心流程
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    // 会话标题最大长度
    private static final int SESSION_TITLE_MAX_LENGTH = 30;
    // 默认会话标题
    private static final String DEFAULT_SESSION_TITLE = "New chat";
    // AI 编排服务
    private final AiOrchestratorService aiOrchestratorService;
    // AI 会话数据访问层
    private final AiSessionMapper aiSessionMapper;

    public AiChatServiceImpl(AiOrchestratorService aiOrchestratorService,
                             AiSessionMapper aiSessionMapper) {
        this.aiOrchestratorService = aiOrchestratorService;
        this.aiSessionMapper = aiSessionMapper;
    }

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatReqDTO requestParam) {
        // 获取当前登录用户上下文
        AiAuthenticatedUserContext authenticatedUser;
        try {
            authenticatedUser = AiUserContextUtil.getAuthenticatedUser();
        } catch (Exception ex) {
            return Flux.just(AiStreamChunk.error(ex.getMessage()));
        }

        // 准备请求上下文（含会话创建/校验）
        AiChatRequestContext context;
        try {
            context = prepareContext(requestParam, authenticatedUser);
        } catch (Exception ex) {
            return Flux.just(AiStreamChunk.error(ex.getMessage()));
        }

        // 构建当前用户消息
        AiChatMessage userMessage = AiChatMessage.user(
                context.getSessionId(),
                authenticatedUser.getUserId(),
                requestParam.getMessage());

        // 委托编排服务执行核心流程
        return aiOrchestratorService.orchestrate(context, userMessage)
                .doOnComplete(() -> log.info("AI 聊天流完成: sessionId={}, userId={}",
                        context.getSessionId(), context.getUserId()))
                .doOnError(ex -> log.error("AI 聊天流异常: sessionId={}, userId={}, error={}",
                        context.getSessionId(), context.getUserId(), ex.getMessage(), ex))
                .onErrorResume(ex -> Flux.just(AiStreamChunk.error(ex.getMessage())));
    }

    /**
     * 准备请求上下文：确保会话存在并设置基础字段
     */
    private AiChatRequestContext prepareContext(AiChatReqDTO requestParam,
                                                AiAuthenticatedUserContext authenticatedUser) {
        // 构建 AI 聊天请求上下文
        AiChatRequestContext context = AiChatRequestContext.builder()
                .sessionId(requestParam.getSessionId())
                .authenticatedUser(authenticatedUser)
                .enableTools(!Boolean.FALSE.equals(requestParam.getEnableTools()))
                .build();

        // 确保会话存在
        ensureSession(context, requestParam);
        return context;
    }

    /**
     * 确保会话 ID 存在，不存在则创建
     */
    private void ensureSession(AiChatRequestContext context, AiChatReqDTO requestParam) {
        if (context.getSessionId() != null) {
            AiSessionDO sessionDO = aiSessionMapper.selectById(context.getSessionId());
            if (sessionDO == null) {
                throw new ClientException("会话不存在或无权访问");
            }
            if (!context.getUserId().equals(sessionDO.getUserId())) {
                throw new ClientException("会话已被其他用户占用");
            }
            return;
        }
        AiSessionDO sessionDO = new AiSessionDO();
        sessionDO.setUserId(context.getUserId());
        sessionDO.setTitle(buildSessionTitle(requestParam.getMessage()));
        sessionDO.setStatus(0);
        aiSessionMapper.insert(sessionDO);
        context.setSessionId(sessionDO.getId());
        context.setNewSession(true);
        context.setKnownTurnCount(0);
    }

    /**
     * 根据用户首条消息构建会话标题
     */
    private String buildSessionTitle(String message) {
        if (message == null || message.isBlank()) {
            return DEFAULT_SESSION_TITLE;
        }
        String title = message.trim().replaceAll("\\s+", " ");
        return title.length() <= SESSION_TITLE_MAX_LENGTH ? title : title.substring(0, SESSION_TITLE_MAX_LENGTH);
    }
}
