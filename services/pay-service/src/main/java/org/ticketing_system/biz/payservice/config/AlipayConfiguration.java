package org.ticketing_system.biz.payservice.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ticketing_system.framework.starter.common.toolkit.BeanUtil;

/**
 * 支付宝客户端配置
 */
@Configuration
public class AlipayConfiguration {

    @Bean
    @SneakyThrows
    public AlipayClient alipayClient(AliPayProperties aliPayProperties) {
        AlipayConfig alipayConfig = BeanUtil.convert(aliPayProperties, AlipayConfig.class);
        return new DefaultAlipayClient(alipayConfig);
    }
}
