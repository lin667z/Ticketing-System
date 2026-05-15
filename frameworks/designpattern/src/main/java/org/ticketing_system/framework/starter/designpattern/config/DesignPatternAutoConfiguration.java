package org.ticketing_system.framework.starter.designpattern.config;

import org.ticketing_system.framework.starter.bases.config.ApplicationBaseAutoConfiguration;
import org.ticketing_system.framework.starter.designpattern.chain.AbstractChainContext;
import org.ticketing_system.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 设计模式自动装配
 * @author lin667z
 */
@ImportAutoConfiguration(ApplicationBaseAutoConfiguration.class)
public class DesignPatternAutoConfiguration {

    /**
     * 策略模式选择器
     */
    @Bean
    public AbstractStrategyChoose abstractStrategyChoose() {
        return new AbstractStrategyChoose();
    }

    /**
     * 责任链上下文
     */
    @Bean
    public AbstractChainContext abstractChainContext() {
        return new AbstractChainContext();
    }
}


