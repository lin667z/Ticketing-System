package org.ticketing_system.biz.aiservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiMemoryMapper;
import org.ticketing_system.biz.aiservice.dto.req.AiMemoryReqDTO;
import org.ticketing_system.biz.aiservice.dto.resp.AiMemoryRespDTO;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.model.AiUserProfile;
import org.ticketing_system.biz.aiservice.remote.UserProfileWebClient;
import org.ticketing_system.biz.aiservice.remote.dto.UserQueryActualRespDTO;
import org.ticketing_system.biz.aiservice.service.AiUserProfileService;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;
import org.ticketing_system.framework.starter.convention.exception.ClientException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 用户画像服务实现，提供 Redis 缓存支持和异步画像构建
 */
@Slf4j
@Service
public class AiUserProfileServiceImpl implements AiUserProfileService {

    // 已认证用户状态
    private static final int VERIFIED_USER_STATUS = 1;
    // 长期记忆类型
    private static final int LONG_TERM_MEMORY_TYPE = 1;
    // 默认记忆权重
    private static final int DEFAULT_MEMORY_WEIGHT = 0;
    // 提示词中最大记忆数量
    private static final int MAX_PROMPT_MEMORY_COUNT = 8;
    // 最大长期记忆数量限制
    private static final int MAX_LONG_TERM_MEMORY_COUNT = 1;
    // 默认分页起始页码
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final String FALLBACK_USER_TAG = "authenticated user";

    // 用户画像缓存 Key 前缀 + 版本号
    private static final String USER_PROFILE_CACHE_KEY_PREFIX = "ai:user:profile:%s:v1";

    // 缓存空值标记，用于防止缓存穿透
    private static final String CACHE_NULL_MARKER = "__NULL__";

    // 空值缓存 TTL（秒）
    private static final long NULL_CACHE_TTL_SECONDS = 60L;

    private final UserProfileWebClient userProfileWebClient;
    private final AiMemoryMapper aiMemoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;

    public AiUserProfileServiceImpl(UserProfileWebClient userProfileWebClient,
            AiMemoryMapper aiMemoryMapper,
            StringRedisTemplate stringRedisTemplate,
            AiProperties aiProperties) {
        this.userProfileWebClient = userProfileWebClient;
        this.aiMemoryMapper = aiMemoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public Mono<AiUserProfile> validateAndBuildProfile(AiAuthenticatedUserContext userContext,
            AiChatRequestContext requestContext) {
        // 先尝试从缓存获取画像，命中则直接返回
        return getCachedProfile(userContext)
                .switchIfEmpty(
                        // 缓存未命中，异步构建画像并验证实名状态
                        buildProfile(userContext, requestContext)
                                .flatMap(profile -> {
                                    if (!Objects.equals(profile.getVerifyStatus(), VERIFIED_USER_STATUS)) {
                                        return Mono.error(new ClientException("用户未实名认证"));
                                    }
                                    // 构建成功后写入缓存
                                    if (!Boolean.TRUE.equals(profile.getDegraded())) {
                                        cacheProfile(userContext, profile);
                                    }
                                    return Mono.just(profile);
                                }))
                .flatMap(profile -> {
                    // 再次校验实名状态
                    if (!Objects.equals(profile.getVerifyStatus(), VERIFIED_USER_STATUS)) {
                        return Mono.error(new ClientException("用户未实名认证"));
                    }
                    return Mono.just(profile);
                });
    }

    @Override
    public Mono<AiUserProfile> buildProfile(AiAuthenticatedUserContext userContext,
            AiChatRequestContext requestContext) {
        // 确保用户已登录
        requireAuthenticatedUser(userContext);

        // 异步查询用户信息
        Mono<UserQueryActualRespDTO> userInfoMono = userProfileWebClient
                .queryUserByUsername(userContext.getUsername(), userContext.getToken())
                .onErrorResume(ex -> {
                    if (isTransientUserProfileFailure(ex)) {
                        log.warn("User profile remote query degraded: userId={}, username={}, error={}",
                                userContext.getUserId(), userContext.getUsername(), ex.getMessage());
                        return Mono.just(buildFallbackUserInfo(userContext));
                    }
                    return Mono.error(ex);
                });

        // 异步查询长期记忆
        Mono<List<String>> memoryMono = Mono
                .fromCallable(
                        () -> queryLongTermMemoryEntities(userContext.getUserId(), MAX_PROMPT_MEMORY_COUNT).stream()
                                .map(AiMemoryDO::getMemoryContent)
                                .filter(Objects::nonNull)
                                .toList())
                .subscribeOn(Schedulers.boundedElastic());

        // 并行获取数据并组装画像
        return Mono.zip(userInfoMono, memoryMono)
                .map(tuple -> {
                    UserQueryActualRespDTO userInfo = tuple.getT1();
                    List<String> memoryContents = tuple.getT2();
                    return AiUserProfile.builder()
                            .username(userInfo.getUsername())
                            .realName(userInfo.getRealName())
                            .idType(userInfo.getIdType())
                            .userType(userInfo.getUserType())
                            .verifyStatus(userInfo.getVerifyStatus())
                            .userTag(resolveUserTag(userInfo))
                            .preferences(memoryContents.isEmpty() ? "未配置" : String.join("；", memoryContents))
                            .memoryContents(memoryContents)
                            .degraded(userInfo instanceof FallbackUserQueryActualRespDTO)
                            .build();
                });
    }

    private boolean isTransientUserProfileFailure(Throwable ex) {
        return ex instanceof TimeoutException || ex instanceof WebClientRequestException;
    }

    private UserQueryActualRespDTO buildFallbackUserInfo(AiAuthenticatedUserContext userContext) {
        FallbackUserQueryActualRespDTO fallback = new FallbackUserQueryActualRespDTO();
        fallback.setUsername(userContext.getUsername());
        fallback.setRealName(userContext.getRealName());
        fallback.setVerifyStatus(VERIFIED_USER_STATUS);
        return fallback;
    }

    private static final class FallbackUserQueryActualRespDTO extends UserQueryActualRespDTO {
    }

    // ==================== 缓存操作 ====================

    /**
     * 从 Redis 缓存获取用户画像，支持防穿透策略
     */
    private Mono<AiUserProfile> getCachedProfile(AiAuthenticatedUserContext userContext) {
        if (userContext.getUserId() == null) {
            return Mono.empty();
        }

        String cacheKey = buildProfileCacheKey(userContext.getUserId());

        return Mono.fromCallable(() -> stringRedisTemplate.opsForValue().get(cacheKey))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cachedValue -> {
                    if (cachedValue == null) {
                        return Mono.empty();
                    }
                    // 命中空值标记
                    if (CACHE_NULL_MARKER.equals(cachedValue)) {
                        log.debug("用户画像缓存命中空值标记: userId={}", userContext.getUserId());
                        return Mono.empty();
                    }
                    // 反序列化画像对象
                    try {
                        AiUserProfile profile = JSON.parseObject(cachedValue, AiUserProfile.class);
                        if (profile != null) {
                            log.debug("用户画像缓存命中: userId={}", userContext.getUserId());
                            return Mono.just(profile);
                        }
                    } catch (Exception e) {
                        log.warn("用户画像缓存反序列化失败: userId={}, error={}",
                                userContext.getUserId(), e.getMessage());
                    }
                    return Mono.empty();
                })
                .onErrorResume(ex -> {
                    log.warn("读取用户画像缓存异常: userId={}, error={}",
                            userContext.getUserId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 将用户画像写入 Redis 缓存
     */
    private void cacheProfile(AiAuthenticatedUserContext userContext, AiUserProfile profile) {
        if (userContext.getUserId() == null || profile == null) {
            return;
        }

        String cacheKey = buildProfileCacheKey(userContext.getUserId());
        int ttlSeconds = aiProperties.getCache() != null
                ? aiProperties.getCache().getUserProfileTtlSeconds()
                : 300;

        try {
            String jsonValue = JSON.toJSONString(profile);
            stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, ttlSeconds, TimeUnit.SECONDS);
            log.debug("用户画像已缓存: userId={}, ttl={}s", userContext.getUserId(), ttlSeconds);
        } catch (Exception e) {
            log.warn("用户画像缓存写入失败: userId={}, error={}", userContext.getUserId(), e.getMessage());
        }
    }

    /**
     * 构建版本化的用户画像缓存 Key
     */
    private String buildProfileCacheKey(Long userId) {
        return String.format(USER_PROFILE_CACHE_KEY_PREFIX, userId);
    }

    @Override
    public List<AiMemoryRespDTO> listLongTermMemories(AiAuthenticatedUserContext userContext) {
        requireAuthenticatedUser(userContext);
        return queryLongTermMemoryEntities(userContext.getUserId(), null).stream()
                .map(this::toResp)
                .toList();
    }

    @Override
    public AiMemoryRespDTO createLongTermMemory(AiAuthenticatedUserContext userContext, AiMemoryReqDTO requestParam) {
        requireAuthenticatedUser(userContext);
        if (countLongTermMemories(userContext.getUserId()) >= MAX_LONG_TERM_MEMORY_COUNT) {
            throw new ClientException("长期记忆数量已达上限");
        }
        AiMemoryDO memoryDO = new AiMemoryDO();
        memoryDO.setUserId(userContext.getUserId());
        memoryDO.setMemoryKey(requestParam.getMemoryKey());
        memoryDO.setMemoryContent(requestParam.getMemoryContent());
        memoryDO.setMemoryType(LONG_TERM_MEMORY_TYPE);
        memoryDO.setWeight(requestParam.getWeight() == null ? DEFAULT_MEMORY_WEIGHT : requestParam.getWeight());
        memoryDO.setExpireTime(requestParam.getExpireTime());
        aiMemoryMapper.insert(memoryDO);
        return toResp(memoryDO);
    }

    @Override
    public AiMemoryRespDTO updateLongTermMemory(AiAuthenticatedUserContext userContext, Long id,
            AiMemoryReqDTO requestParam) {
        requireAuthenticatedUser(userContext);
        if (id == null) {
            throw new ClientException("记忆 ID 不能为空");
        }
        LambdaUpdateWrapper<AiMemoryDO> updateWrapper = Wrappers.lambdaUpdate(AiMemoryDO.class)
                .eq(AiMemoryDO::getId, id)
                .eq(AiMemoryDO::getUserId, userContext.getUserId())
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .set(AiMemoryDO::getMemoryKey, requestParam.getMemoryKey())
                .set(AiMemoryDO::getMemoryContent, requestParam.getMemoryContent())
                .set(AiMemoryDO::getWeight,
                        requestParam.getWeight() == null ? DEFAULT_MEMORY_WEIGHT : requestParam.getWeight())
                .set(AiMemoryDO::getExpireTime, requestParam.getExpireTime());
        int rows = aiMemoryMapper.update(null, updateWrapper);
        if (rows <= 0) {
            throw new ClientException("记忆不存在或无权访问");
        }
        return toResp(selectOwnedMemory(userContext.getUserId(), id));
    }

    @Override
    public void deleteLongTermMemory(AiAuthenticatedUserContext userContext, Long id) {
        requireAuthenticatedUser(userContext);
        if (id == null) {
            throw new ClientException("记忆 ID 不能为空");
        }
        LambdaQueryWrapper<AiMemoryDO> deleteWrapper = Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getId, id)
                .eq(AiMemoryDO::getUserId, userContext.getUserId())
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE);
        int rows = aiMemoryMapper.delete(deleteWrapper);
        if (rows <= 0) {
            throw new ClientException("记忆不存在或无权访问");
        }
    }

    /**
     * 校验用户是否已登录
     */
    private void requireAuthenticatedUser(AiAuthenticatedUserContext userContext) {
        if (userContext == null || userContext.getUserId() == null
                || userContext.getUsername() == null || userContext.getUsername().isBlank()) {
            throw new ClientException("用户未登录");
        }
    }

    /**
     * 统计用户的长期记忆数量
     */
    private Long countLongTermMemories(Long userId) {
        return aiMemoryMapper.selectCount(Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE));
    }

    /**
     * 查询长期记忆实体列表
     *
     * <p>
     * 使用 MyBatis-Plus {@link Page} 进行分页查询，避免字符串拼接 LIMIT
     * </p>
     */
    private List<AiMemoryDO> queryLongTermMemoryEntities(Long userId, Integer limit) {
        Date now = new Date();
        LambdaQueryWrapper<AiMemoryDO> queryWrapper = Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE)
                .and(wrapper -> wrapper.isNull(AiMemoryDO::getExpireTime).or().gt(AiMemoryDO::getExpireTime, now))
                .orderByDesc(AiMemoryDO::getWeight)
                .orderByDesc(AiMemoryDO::getUpdateTime);
        if (limit != null && limit > 0) {
            Page<AiMemoryDO> page = new Page<>(DEFAULT_PAGE_NUM, limit);
            return aiMemoryMapper.selectPage(page, queryWrapper).getRecords();
        }
        return aiMemoryMapper.selectList(queryWrapper);
    }

    /**
     * 查询属于该用户的记忆
     */
    private AiMemoryDO selectOwnedMemory(Long userId, Long id) {
        AiMemoryDO memoryDO = aiMemoryMapper.selectOne(Wrappers.lambdaQuery(AiMemoryDO.class)
                .eq(AiMemoryDO::getId, id)
                .eq(AiMemoryDO::getUserId, userId)
                .eq(AiMemoryDO::getMemoryType, LONG_TERM_MEMORY_TYPE));
        if (memoryDO == null) {
            throw new ClientException("记忆不存在或无权访问");
        }
        return memoryDO;
    }

    /**
     * 实体转响应 DTO
     */
    private AiMemoryRespDTO toResp(AiMemoryDO memoryDO) {
        return BeanUtil.convert(memoryDO, AiMemoryRespDTO.class);
    }

    /**
     * 解析用户标签
     */
    private String resolveUserTag(UserQueryActualRespDTO userInfo) {
        if (userInfo instanceof FallbackUserQueryActualRespDTO) {
            return FALLBACK_USER_TAG;
        }
        if (Objects.equals(userInfo.getUserType(), 1)) {
            return "student passenger";
        }
        return "regular passenger";
    }
}
