package org.ticketing_system.biz.aiservice.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiMessageMapper;
import org.ticketing_system.framework.starter.common.enums.DelEnum;

import java.util.Collections;
import java.util.List;

/**
 * 事务性聊天记忆服务，提供基于 MyBatis-Plus 的会话历史存储、分页查询与逻辑删除能力
 */
@Slf4j
@Service
public class ChatMemoryService {

    // 批量插入的批次大小参考值
    private static final int BATCH_SIZE_HINT = 50;
    // 未删除标记值
    private static final int DEL_FLAG_NORMAL = DelEnum.NORMAL.code();
    // 默认最大历史消息数
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final AiMessageMapper aiMessageMapper;

    public ChatMemoryService(AiMessageMapper aiMessageMapper) {
        this.aiMessageMapper = aiMessageMapper;
    }

    /**
     * 获取指定会话最近的历史消息列表，使用分页查询保证性能与安全
     *
     * @param sessionId   会话 ID
     * @param maxMessages 最大消息条数
     * @return 按时间升序排列的消息列表
     */
    @Transactional(readOnly = true)
    public List<AiMessageDO> getRecentMessages(Long sessionId, int maxMessages) {
        if (sessionId == null || maxMessages <= 0) {
            log.debug("getRecentMessages 参数无效: sessionId={}, maxMessages={}", sessionId, maxMessages);
            return Collections.emptyList();
        }

        int queryLimit = Math.min(maxMessages, DEFAULT_MAX_MESSAGES * 2);

        Page<AiMessageDO> page = new Page<>(1, queryLimit);
        LambdaQueryWrapper<AiMessageDO> wrapper = Wrappers.lambdaQuery(AiMessageDO.class)
                .eq(AiMessageDO::getSessionId, sessionId)
                .eq(AiMessageDO::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiMessageDO::getCreateTime);

        Page<AiMessageDO> result = aiMessageMapper.selectPage(page, wrapper);
        List<AiMessageDO> records = result.getRecords();

        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(records);

        log.debug("查询会话历史: sessionId={}, total={}, returned={}", sessionId, result.getTotal(), records.size());
        return records;
    }

    /**
     * 获取最近 N 轮完整对话
     *
     * @param sessionId 会话 ID
     * @param pairCount 对话轮数
     * @return 最近消息，按创建时间升序
     */
    @Transactional(readOnly = true)
    public List<AiMessageDO> getRecentTurns(Long sessionId, int pairCount) {
        if (pairCount <= 0) {
            return Collections.emptyList();
        }
        return getRecentMessages(sessionId, pairCount * 2);
    }

    /**
     * 获取会话全量历史，用于 Redis 上下文丢失后的恢复
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 全量消息，按创建时间升序
     */
    @Transactional(readOnly = true)
    public List<AiMessageDO> getAllMessages(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AiMessageDO> wrapper = Wrappers.lambdaQuery(AiMessageDO.class)
                .eq(AiMessageDO::getSessionId, sessionId)
                .eq(AiMessageDO::getUserId, userId)
                .eq(AiMessageDO::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiMessageDO::getCreateTime);
        return aiMessageMapper.selectList(wrapper);
    }

    /**
     * 将单条消息持久化到数据库
     *
     * @param message 消息实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(AiMessageDO message) {
        if (message == null) {
            log.warn("saveMessage: message 为 null，跳过");
            return;
        }
        int rows = aiMessageMapper.insert(message);
        log.debug("保存单条消息: id={}, sessionId={}, role={}, rows={}",
                message.getId(), message.getSessionId(), message.getRole(), rows);
    }

    /**
     * 批量保存消息，利用事务保证同一轮对话消息的原子性写入
     *
     * @param messages 消息实体列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessagesBatch(List<AiMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("saveMessagesBatch: 消息列表为空，跳过");
            return;
        }
        // 过滤 null 元素
        List<AiMessageDO> filtered = messages.stream()
                .filter(msg -> msg != null)
                .toList();
        if (filtered.isEmpty()) {
            return;
        }
        Long sessionId = filtered.get(0).getSessionId();
        int count = 0;
        for (AiMessageDO message : filtered) {
            aiMessageMapper.insert(message);
            count++;
        }
        log.info("批量保存消息完成: count={}, sessionId={}", count, sessionId);
    }

    /**
     * 逻辑删除指定会话的所有历史消息
     *
     * @param sessionId 会话 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionMessages(Long sessionId) {
        if (sessionId == null) {
            log.warn("deleteSessionMessages: sessionId 为 null，跳过");
            return;
        }
        LambdaQueryWrapper<AiMessageDO> wrapper = Wrappers.lambdaQuery(AiMessageDO.class)
                .eq(AiMessageDO::getSessionId, sessionId)
                .eq(AiMessageDO::getDelFlag, DEL_FLAG_NORMAL);
        int rows = aiMessageMapper.delete(wrapper);
        log.info("逻辑删除会话消息: sessionId={}, deletedRows={}", sessionId, rows);
    }
}
