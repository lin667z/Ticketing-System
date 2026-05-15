package org.ticketing_system.framework.starter.log.config;

import org.ticketing_system.framework.starter.log.core.ILogPrintAspect;
import org.ticketing_system.framework.starter.log.annotation.ILog;
import org.springframework.context.annotation.Bean;

/**
 * 日志自动装配
 * @author lin667z
 */
public class LogAutoConfiguration {

    /**
     * {@link ILog} 日志打印 AOP 切面
     */
    @Bean
    public ILogPrintAspect iLogPrintAspect() {
        return new ILogPrintAspect();
    }
}


