package org.ticketing_system.framework.starter.cache.core;

/**
 * 缓存查询为空
 * @author lin667z
 */
@FunctionalInterface
public interface CacheGetIfAbsent<T> {

    /**
     * 如果查询结果为空，执行逻辑
     */
    void execute(T param);
}


