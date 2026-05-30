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
import org.ticketing_system.biz.aiservice.common.util.AiUserContextUtil;
import org.ticketing_system.biz.aiservice.dto.req.AiMemoryReqDTO;
import org.ticketing_system.biz.aiservice.dto.resp.AiMemoryRespDTO;
import org.ticketing_system.biz.aiservice.service.AiUserProfileService;
import org.ticketing_system.framework.starter.convention.result.Result;

import java.util.List;

/**
 * AI 长期记忆管理控制层
 */
@RestController
@RequiredArgsConstructor
public class AiProfileMemoryController {

    private final AiUserProfileService aiUserProfileService;

    /**
     * 查询长期记忆列表
     */
    @GetMapping("/api/ai-service/profile/memory")
    public Result<List<AiMemoryRespDTO>> listLongTermMemories() {
        return success(aiUserProfileService.listLongTermMemories(AiUserContextUtil.getAuthenticatedUser()));
    }

    /**
     * 创建长期记忆
     */
    @PostMapping("/api/ai-service/profile/memory")
    public Result<AiMemoryRespDTO> createLongTermMemory(@RequestBody @Valid AiMemoryReqDTO requestParam) {
        return success(aiUserProfileService.createLongTermMemory(AiUserContextUtil.getAuthenticatedUser(), requestParam));
    }

    /**
     * 更新长期记忆
     */
    @PutMapping("/api/ai-service/profile/memory/{id}")
    public Result<AiMemoryRespDTO> updateLongTermMemory(@PathVariable("id") Long id,
                                                        @RequestBody @Valid AiMemoryReqDTO requestParam) {
        return success(aiUserProfileService.updateLongTermMemory(AiUserContextUtil.getAuthenticatedUser(), id, requestParam));
    }

    /**
     * 删除长期记忆
     */
    @DeleteMapping("/api/ai-service/profile/memory/{id}")
    public Result<Void> deleteLongTermMemory(@PathVariable("id") Long id) {
        aiUserProfileService.deleteLongTermMemory(AiUserContextUtil.getAuthenticatedUser(), id);
        return success();
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
