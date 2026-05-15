package org.ticketing_system.biz.ticketservice;

import cn.hippo4j.core.enable.EnableDynamicThreadPool;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购票服务应用启动器
 * @author lin667z
 */
@SpringBootApplication
@EnableDynamicThreadPool
@MapperScan("org.ticketing_system.biz.ticketservice.dao.mapper")
@EnableFeignClients("org.ticketing_system.biz.ticketservice.remote")
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}


