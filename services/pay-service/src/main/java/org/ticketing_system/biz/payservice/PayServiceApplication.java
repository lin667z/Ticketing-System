package org.ticketing_system.biz.payservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 支付服务应用启动器
 * @author lin667z
 */
@SpringBootApplication
@MapperScan("org.ticketing_system.biz.payservice.dao.mapper")
@EnableFeignClients("org.ticketing_system.biz.payservice.remote")
@EnableRetry
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}


