package org.ticketing_system.biz.aiservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.dao.entity.AiSessionDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiMessageMapper;
import org.ticketing_system.biz.aiservice.dao.mapper.AiSessionMapper;
import org.ticketing_system.biz.aiservice.dto.resp.ConversationDetailRespDTO;
import org.ticketing_system.biz.aiservice.dto.resp.ConversationRespDTO;
import org.ticketing_system.biz.aiservice.memory.MemoryFacade;
import org.ticketing_system.biz.aiservice.service.ConversationService;
import org.ticketing_system.framework.starter.common.enums.DelEnum;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;
import org.ticketing_system.framework.starter.convention.exception.ClientException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 智能客服对话管理服务实现
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    // 默认对话名称
    private static final String DEFAULT_CONVERSATION_TITLE = "新对话";

    // 未删除标记值
    private static final int DEL_FLAG_NORMAL = DelEnum.NORMAL.code();

    // 会话进行中状态
    private static final int SESSION_STATUS_ACTIVE = 0;

    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final MemoryFacade memoryFacade;

    public ConversationServiceImpl(AiSessionMapper aiSessionMapper,
                                   AiMessageMapper aiMessageMapper,
                                   MemoryFacade memoryFacade) {
        this.aiSessionMapper = aiSessionMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.memoryFacade = memoryFacade;
    }

    @Override
    public ConversationRespDTO createConversation(Long userId) {
        if (userId == null) {
            throw new ClientException("用户 ID 不能为空");
        }
        AiSessionDO sessionDO = new AiSessionDO();
        sessionDO.setUserId(userId);
        sessionDO.setTitle(DEFAULT_CONVERSATION_TITLE);
        sessionDO.setStatus(SESSION_STATUS_ACTIVE);
        aiSessionMapper.insert(sessionDO);
        log.info("创建对话: id={}, userId={}", sessionDO.getId(), userId);
        return toConversationResp(sessionDO, 0L);
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        validateOwnership(conversationId, userId);
        AiSessionDO sessionDO = aiSessionMapper.selectById(conversationId);
        // 软删前 finalize 会话：从 L1 取累积事件派生情节（L3），并触发自动消化为长期偏好（L4）
        try {
            memoryFacade.finalizeSession(userId, conversationId);
        } catch (Exception ex) {
            log.warn("会话 finalize 失败（不影响删除）: id={}, userId={}, error={}", conversationId, userId, ex.getMessage());
        }
        LambdaUpdateWrapper<AiSessionDO> updateWrapper = Wrappers.lambdaUpdate(AiSessionDO.class)
                .eq(AiSessionDO::getId, conversationId)
                .eq(AiSessionDO::getUserId, userId)
                .set(AiSessionDO::getDelFlag, DelEnum.DELETE.code());
        aiSessionMapper.update(null, updateWrapper);
        log.info("删除对话: id={}, userId={}, title={}", conversationId, userId, sessionDO.getTitle());
    }

    @Override
    public ConversationRespDTO renameConversation(Long conversationId, Long userId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ClientException("对话名称不能为空");
        }
        validateOwnership(conversationId, userId);
        AiSessionDO sessionDO = aiSessionMapper.selectById(conversationId);
        LambdaUpdateWrapper<AiSessionDO> updateWrapper = Wrappers.lambdaUpdate(AiSessionDO.class)
                .eq(AiSessionDO::getId, conversationId)
                .eq(AiSessionDO::getUserId, userId)
                .set(AiSessionDO::getTitle, newName);
        aiSessionMapper.update(null, updateWrapper);
        long messageCount = countMessages(conversationId);
        AiSessionDO updated = aiSessionMapper.selectById(conversationId);
        log.info("重命名对话: id={}, userId={}, newName={}", conversationId, userId, newName);
        return toConversationResp(updated, messageCount);
    }

    @Override
    public List<ConversationRespDTO> listConversations(Long userId) {
        if (userId == null) {
            throw new ClientException("用户 ID 不能为空");
        }
        LambdaQueryWrapper<AiSessionDO> queryWrapper = Wrappers.lambdaQuery(AiSessionDO.class)
                .eq(AiSessionDO::getUserId, userId)
                .eq(AiSessionDO::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiSessionDO::getUpdateTime);
        List<AiSessionDO> sessions = aiSessionMapper.selectList(queryWrapper);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }
        return sessions.stream()
                .map(session -> toConversationResp(session, countMessages(session.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public ConversationDetailRespDTO getConversationDetail(Long conversationId, Long userId) {
        validateOwnership(conversationId, userId);
        AiSessionDO sessionDO = aiSessionMapper.selectById(conversationId);
        List<AiMessageDO> messages = queryMessagesBySession(conversationId);
        ConversationDetailRespDTO detail = new ConversationDetailRespDTO();
        detail.setId(sessionDO.getId());
        detail.setTitle(sessionDO.getTitle());
        detail.setStatus(sessionDO.getStatus());
        detail.setCreateTime(sessionDO.getCreateTime());
        detail.setUpdateTime(sessionDO.getUpdateTime());
        detail.setMessages(messages.stream()
                .map(this::toMessageResp)
                .collect(Collectors.toList()));
        log.info("查询对话详情: id={}, userId={}, messageCount={}", conversationId, userId, messages.size());
        return detail;
    }

    /**
     * 校验对话归属权
     */
    private void validateOwnership(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw new ClientException("对话 ID 不能为空");
        }
        if (userId == null) {
            throw new ClientException("用户 ID 不能为空");
        }
        AiSessionDO sessionDO = aiSessionMapper.selectById(conversationId);
        if (sessionDO == null || !Objects.equals(sessionDO.getDelFlag(), DEL_FLAG_NORMAL)) {
            throw new ClientException("对话不存在");
        }
        if (!userId.equals(sessionDO.getUserId())) {
            throw new ClientException("无权访问该对话");
        }
    }

    /**
     * 统计某会话下的未删除消息数量
     */
    private long countMessages(Long sessionId) {
        LambdaQueryWrapper<AiMessageDO> queryWrapper = Wrappers.lambdaQuery(AiMessageDO.class)
                .eq(AiMessageDO::getSessionId, sessionId)
                .eq(AiMessageDO::getDelFlag, DEL_FLAG_NORMAL);
        return aiMessageMapper.selectCount(queryWrapper);
    }

    /**
     * 查询某会话下的所有未删除消息（按创建时间升序）
     */
    private List<AiMessageDO> queryMessagesBySession(Long sessionId) {
        LambdaQueryWrapper<AiMessageDO> queryWrapper = Wrappers.lambdaQuery(AiMessageDO.class)
                .eq(AiMessageDO::getSessionId, sessionId)
                .eq(AiMessageDO::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiMessageDO::getCreateTime);
        return aiMessageMapper.selectList(queryWrapper);
    }

    /**
     * 会话实体转为对话响应 DTO
     */
    private ConversationRespDTO toConversationResp(AiSessionDO sessionDO, Long messageCount) {
        ConversationRespDTO resp = BeanUtil.convert(sessionDO, ConversationRespDTO.class);
        resp.setMessageCount(messageCount);
        return resp;
    }

    /**
     * 消息实体转为消息响应 DTO
     */
    private ConversationDetailRespDTO.MessageRespDTO toMessageResp(AiMessageDO messageDO) {
        ConversationDetailRespDTO.MessageRespDTO resp = new ConversationDetailRespDTO.MessageRespDTO();
        resp.setId(messageDO.getId());
        resp.setRole(messageDO.getRole());
        resp.setContent(messageDO.getContent());
        resp.setModelName(messageDO.getModelName());
        resp.setTokenCount(messageDO.getTokenCount());
        resp.setCreateTime(messageDO.getCreateTime());
        return resp;
    }
}
