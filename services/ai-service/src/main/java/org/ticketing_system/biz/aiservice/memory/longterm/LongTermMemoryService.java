package org.ticketing_system.biz.aiservice.memory.longterm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiMemoryMapper;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;

import java.util.Date;
import java.util.List;

/**
 * 长期记忆服务，负责偏好存取与权重排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    // 默认检索 Top-K 数量
    private static final int DEFAULT_RETRIEVAL_TOP_K = 8;
    // 默认分页页码
    private static final long DEFAULT_PAGE_NUM = 1L;
    // 长期记忆类型标识
    private static final int LONG_TERM_MEMORY_TYPE = 1;

    // 记忆 Mapper
    private final AiMemoryMapper aiMemoryMapper;
    // AI 配置属性
    private final AiProperties aiProperties;

    /**
     * 查询用户偏好，按权重降序取 Top-K
     */
    public List<AiMemoryDO> queryForPrompt(Long userId, WorkingMemoryState wm) {
        Date now = new Date();
        int topK = getRetrievalTopK();
        LambdaQueryWrapper<AiMemoryDO> queryWrapper = Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .and(w -> w.isNull(AiMemoryDO::getExpireTime).or().gt(AiMemoryDO::getExpireTime, now))
                .orderByDesc(AiMemoryDO::getWeight)
                .orderByDesc(AiMemoryDO::getUpdateTime);
        Page<AiMemoryDO> page = new Page<>(DEFAULT_PAGE_NUM, topK);
        return aiMemoryMapper.selectPage(page, queryWrapper).getRecords();
    }

    /**
     * 查询用户全部未过期偏好
     */
    public List<AiMemoryDO> listByUserId(Long userId) {
        Date now = new Date();
        LambdaQueryWrapper<AiMemoryDO> queryWrapper = Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .and(w -> w.isNull(AiMemoryDO::getExpireTime).or().gt(AiMemoryDO::getExpireTime, now))
                .orderByDesc(AiMemoryDO::getWeight)
                .orderByDesc(AiMemoryDO::getUpdateTime);
        return aiMemoryMapper.selectList(queryWrapper);
    }

    /**
     * 保存用户偏好记录。
     *
     * <p>按 {@code (userId, preferenceType, preferenceKey)} 去重：已存在则更新值并刷新时间，
     * 不存在才插入；插入后若超过 {@code maxPerUser} 上限则裁剪最旧的偏好。</p>
     */
    public void savePreference(Long userId, PreferenceType preferenceType, String key, String value, String content, String source) {
        if (userId == null || preferenceType == null || key == null || key.isBlank()) {
            return;
        }
        AiMemoryDO existing = aiMemoryMapper.selectOne(Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .eq(AiMemoryDO::getPreferenceType, preferenceType.name())
                .eq(AiMemoryDO::getPreferenceKey, key)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setPreferenceValue(value);
            existing.setMemoryContent(content);
            existing.setSource(source);
            aiMemoryMapper.updateById(existing);
            return;
        }
        AiMemoryDO memory = new AiMemoryDO();
        memory.setUserId(userId);
        memory.setMemoryKey(key);
        memory.setMemoryContent(content);
        memory.setMemoryType(LONG_TERM_MEMORY_TYPE);
        memory.setPreferenceType(preferenceType.name());
        memory.setPreferenceKey(key);
        memory.setPreferenceValue(value);
        memory.setSource(source);
        memory.setWeight(0);
        aiMemoryMapper.insert(memory);
        trimToMaxPerUser(userId);
    }

    /**
     * 裁剪超过上限的偏好：保留权重高、更新近的，删除最旧的。
     */
    private void trimToMaxPerUser(Long userId) {
        int maxPerUser = getMaxPerUser();
        if (maxPerUser <= 0) {
            return;
        }
        Long total = aiMemoryMapper.selectCount(Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE));
        if (total == null || total <= maxPerUser) {
            return;
        }
        List<AiMemoryDO> overflow = aiMemoryMapper.selectList(Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .orderByAsc(AiMemoryDO::getWeight)
                .orderByAsc(AiMemoryDO::getUpdateTime)
                .last("LIMIT " + (total - maxPerUser)));
        for (AiMemoryDO doomed : overflow) {
            aiMemoryMapper.deleteById(doomed.getId());
        }
    }

    /**
     * 获取每用户长期记忆上限
     */
    private int getMaxPerUser() {
        AiProperties.LongTermMemory ltm = aiProperties.getLongTermMemory();
        return ltm == null ? 50 : ltm.getMaxPerUser();
    }

    /**
     * 获取配置的检索 Top-K 值
     */
    private int getRetrievalTopK() {
        AiProperties.LongTermMemory ltm = aiProperties.getLongTermMemory();
        return ltm == null ? DEFAULT_RETRIEVAL_TOP_K : ltm.getRetrievalTopK();
    }
}
