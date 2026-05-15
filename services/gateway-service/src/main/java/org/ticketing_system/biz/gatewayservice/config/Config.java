package org.ticketing_system.biz.gatewayservice.config;

import lombok.Data;

import java.util.List;

/**
 * 过滤器配置
 * @author lin667z
 */
@Data
public class Config {

    /**
     * 黑名单前置路径
     */
    private List<String> blackPathPre;
}


