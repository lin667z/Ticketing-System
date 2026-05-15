package org.ticketing_system.framework.starter.cache.core;

/**
 * 缓存加载器
 * @author lin667z
 */
@FunctionalInterface
public interface CacheLoader<T> {

    /**
     * 加载缓存
     */
    T load();
}


