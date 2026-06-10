package org.ticketing_system.biz.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing_system.biz.aiservice.common.util.AiUserContextUtil;
import org.ticketing_system.biz.aiservice.dao.entity.AiFeedbackDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiFeedbackMapper;
import org.ticketing_system.biz.aiservice.dto.req.AiFeedbackReqDTO;
import org.ticketing_system.framework.starter.convention.result.Result;

/**
 * AI 反馈控制器，提供用户反馈提交接口
 */
@RestController
@RequiredArgsConstructor
public class AiFeedbackController {

    // 反馈 Mapper
    private final AiFeedbackMapper aiFeedbackMapper;

    /**
     * 提交用户反馈
     */
    @PostMapping("/api/ai-service/feedback")
    public Result<Void> submitFeedback(@RequestBody @Valid AiFeedbackReqDTO requestParam) {
        AiFeedbackDO feedback = new AiFeedbackDO();
        feedback.setMessageId(requestParam.getMessageId());
        feedback.setSessionId(requestParam.getSessionId());
        feedback.setUserId(AiUserContextUtil.getAuthenticatedUser().getUserId());
        feedback.setFeedbackType(requestParam.getFeedbackType());
        feedback.setFeedbackContent(requestParam.getFeedbackContent());
        aiFeedbackMapper.insert(feedback);
        return success();
    }

    /**
     * 构造成功响应
     */
    private Result<Void> success() {
        return new Result<Void>().setCode(Result.SUCCESS_CODE);
    }
}
