package org.ticketing_system.biz.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.common.util.AiUserContextUtil;
import org.ticketing_system.biz.aiservice.dto.req.RenameConversationReqDTO;
import org.ticketing_system.biz.aiservice.dto.resp.ConversationDetailRespDTO;
import org.ticketing_system.biz.aiservice.dto.resp.ConversationRespDTO;
import org.ticketing_system.biz.aiservice.service.ConversationService;
import org.ticketing_system.framework.starter.convention.result.Result;

import java.util.List;

/**
 * 智能客服对话管理控制层
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建新对话
     */
    @PostMapping("/api/ai-service/conversation/create")
    public Result<ConversationRespDTO> createConversation() {
        AiAuthenticatedUserContext user = AiUserContextUtil.getAuthenticatedUser();
        return success(conversationService.createConversation(user.getUserId()));
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/api/ai-service/conversation/{id}")
    public Result<Void> deleteConversation(@PathVariable("id") Long id) {
        AiAuthenticatedUserContext user = AiUserContextUtil.getAuthenticatedUser();
        conversationService.deleteConversation(id, user.getUserId());
        return success();
    }

    /**
     * 重命名对话
     */
    @PutMapping("/api/ai-service/conversation/{id}/rename")
    public Result<ConversationRespDTO> renameConversation(@PathVariable("id") Long id,
                                                          @RequestBody @Valid RenameConversationReqDTO requestParam) {
        AiAuthenticatedUserContext user = AiUserContextUtil.getAuthenticatedUser();
        return success(conversationService.renameConversation(id, user.getUserId(), requestParam.getNewName()));
    }

    /**
     * 获取对话列表
     */
    @GetMapping("/api/ai-service/conversation/list")
    public Result<List<ConversationRespDTO>> listConversations() {
        AiAuthenticatedUserContext user = AiUserContextUtil.getAuthenticatedUser();
        return success(conversationService.listConversations(user.getUserId()));
    }

    /**
     * 获取对话详情
     */
    @GetMapping("/api/ai-service/conversation/{id}")
    public Result<ConversationDetailRespDTO> getConversationDetail(@PathVariable("id") Long id) {
        AiAuthenticatedUserContext user = AiUserContextUtil.getAuthenticatedUser();
        return success(conversationService.getConversationDetail(id, user.getUserId()));
    }

    /**
     * 构建无数据成功响应（WebFlux 专用）
     */
    private Result<Void> success() {
        return new Result<Void>().setCode(Result.SUCCESS_CODE);
    }

    /**
     * 构建带数据成功响应（WebFlux 专用）
     */
    private <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data);
    }
}
