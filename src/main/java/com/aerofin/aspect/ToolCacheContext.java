package com.aerofin.aspect;

/**
 * 工具缓存上下文（基于 ThreadLocal 传递缓存命中信息）
 *
 * 用途：
 * - 在工具方法内部标记本次调用是否命中缓存
 * - 在 AOP 切面中读取该标记，打到监控指标里
 *
 * 这样既不用修改所有方法签名，也避免通过日志“猜测”是否命中缓存。
 */
public final class ToolCacheContext {

    private ToolCacheContext() {
    }

    /**
     * 当前线程最近一次工具调用是否命中缓存
     */
    private static final ThreadLocal<Boolean> CACHE_HIT = new ThreadLocal<>();

    public static void markCacheHit(boolean hit) {
        CACHE_HIT.set(hit);
    }

    public static boolean isCacheHit() {
        Boolean value = CACHE_HIT.get();
        return value != null && value;
    }

    /**
     * 清理上下文，避免线程复用导致脏数据
     */
    public static void clear() {
        CACHE_HIT.remove();
    }
}


