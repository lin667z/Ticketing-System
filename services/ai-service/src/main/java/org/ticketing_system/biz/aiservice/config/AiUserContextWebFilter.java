package org.ticketing_system.biz.aiservice.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.ticketing_system.framework.starter.bases.constant.UserConstant;
import org.ticketing_system.frameworks.starter.user.core.UserContext;
import org.ticketing_system.frameworks.starter.user.core.UserInfoDTO;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 用于 WebFlux 环境的用户上下文信息过滤器，解析并绑定用户信息
 */
@Component
public class AiUserContextWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        bindUserContext(exchange.getRequest().getHeaders());
        return chain.filter(exchange)
                .doFinally(ignored -> UserContext.removeUser());
    }

    /**
     * 从请求头中提取用户信息并绑定到 UserContext
     */
    private void bindUserContext(HttpHeaders headers) {
        String userId = headers.getFirst(UserConstant.USER_ID_KEY);
        if (!StringUtils.hasText(userId)) {
            return;
        }
        UserContext.setUser(UserInfoDTO.builder()
                .userId(userId)
                .username(decode(headers.getFirst(UserConstant.USER_NAME_KEY)))
                .realName(decode(headers.getFirst(UserConstant.REAL_NAME_KEY)))
                .token(headers.getFirst(UserConstant.USER_TOKEN_KEY))
                .build());
    }

    /**
     * 对请求头中的用户信息进行 URL 解码
     */
    private String decode(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return URLDecoder.decode(value, UTF_8);
    }
}
