package org.ticketing_system.biz.aiservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;
import org.ticketing_system.biz.aiservice.dao.mapper.AiMemoryMapper;
import org.ticketing_system.biz.aiservice.model.AiUserProfile;
import org.ticketing_system.biz.aiservice.remote.UserProfileWebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiUserProfileServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildProfileFallsBackWhenUserServiceTimesOut() {
        UserProfileWebClient userProfileWebClient = mock(UserProfileWebClient.class);
        AiMemoryMapper aiMemoryMapper = mock(AiMemoryMapper.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        AiUserProfileServiceImpl service = new AiUserProfileServiceImpl(
                userProfileWebClient,
                aiMemoryMapper,
                stringRedisTemplate,
                new AiProperties());
        AiAuthenticatedUserContext userContext = AiAuthenticatedUserContext.builder()
                .userId(1L)
                .username("alice")
                .realName("Alice")
                .token("token")
                .build();
        when(userProfileWebClient.queryUserByUsername("alice", "token"))
                .thenReturn(Mono.error(new TimeoutException("timeout")));
        when(aiMemoryMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<AiMemoryDO>());

        AiUserProfile profile = service.buildProfile(userContext, AiChatRequestContext.builder().build()).block();

        assertThat(profile).isNotNull();
        assertThat(profile.getUsername()).isEqualTo("alice");
        assertThat(profile.getRealName()).isEqualTo("Alice");
        assertThat(profile.getVerifyStatus()).isEqualTo(1);
        assertThat(profile.getUserTag()).isEqualTo("authenticated user");
        assertThat(profile.getDegraded()).isTrue();
    }
}
