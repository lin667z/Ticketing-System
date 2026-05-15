package org.ticketing_system.biz.orderservice;

import cn.crane4j.spring.boot.annotation.EnableCrane4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务应用启动器
 * @author lin667z
 */
@SpringBootApplication
@MapperScan("org.ticketing_system.biz.orderservice.dao.mapper")
@EnableFeignClients("org.ticketing_system.biz.orderservice.remote")
@EnableCrane4j(enumPackages = "org.ticketing_system.biz.orderservice.common.enums")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}


