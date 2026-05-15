package org.ticketing_system.framework.starter.database.handler;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.ticketing_system.framework.starter.distributedid.toolkit.SnowflakeIdUtil;

/**
 * 自定义雪花算法生成器
 * @author lin667z
 */
public class CustomIdGenerator implements IdentifierGenerator {

    @Override
    public Number nextId(Object entity) {
        return SnowflakeIdUtil.nextId();
    }
}


