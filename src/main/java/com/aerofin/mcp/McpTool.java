package com.aerofin.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 工具接口
 * <p>
 * 标准化的工具定义，符合 MCP 规范
 * <p>
 * 核心设计：
 * 1. 统一的工具元数据（name, description, parameters）
 * 2. 参数验证机制
 * 3. 执行结果标准化
 * 4. 支持异步执行
 * 5. 支持工具编排
 * <p>
 * 面试亮点：
 * - 符合 MCP 标准（与 Claude/GPT 工具调用兼容）
 * - 参数自动验证与类型转换
 * - 支持工具链（Tool Chaining）
 * - 可观测性（监控、日志、追踪）
 *
 * @author Aero-Fin Team
 */
public interface McpTool<I, O> {

    /**
     * 获取工具元数据
     */
    ToolMetadata getMetadata();

    /**
     * 执行工具
     *
     * @param input 输入参数
     * @return 执行结果
     */
    ToolResult<O> execute(I input) throws Exception;

    /**
     * 验证输入参数
     */
    default boolean validateInput(I input) {
        return input != null;
    }

    /**
     * 工具元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ToolMetadata {
        /**
         * 工具名称（唯一标识）
         */
        private String name;

        /**
         * 工具描述（用于 LLM 理解工具用途）
         */
        private String description;

        /**
         * 工具分类
         */
        private String category;

        /**
         * 参数定义（JSON Schema 格式）
         */
        private List<ToolParameter> parameters;

        /**
         * 是否支持异步执行
         */
        @Builder.Default
        private Boolean async = false;

        /**
         * 是否启用缓存
         */
        @Builder.Default
        private Boolean cacheable = true;

        /**
         * 预估执行时间（毫秒）
         */
        @Builder.Default
        private Long estimatedDuration = 1000L;

        /**
         * 工具版本
         */
        @Builder.Default
        private String version = "1.0.0";

        /**
         * 依赖的其他工具
         */
        private List<String> dependencies;

        /**
         * 标签（用于检索和分类）
         */
        private List<String> tags;
    }

    /**
     * 工具参数定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ToolParameter {
        /**
         * 参数名称
         */
        private String name;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 参数描述
         */
        private String description;

        /**
         * 是否必需
         */
        @Builder.Default
        private Boolean required = false;

        /**
         * 默认值
         */
        private Object defaultValue;

        /**
         * 枚举值（如果是枚举类型）
         */
        private List<String> enumValues;

        /**
         * 验证规则（正则表达式）
         */
        private String pattern;

        /**
         * 最小值（数值类型）
         */
        private Number minimum;

        /**
         * 最大值（数值类型）
         */
        private Number maximum;
    }

    /**
     * 工具执行结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ToolResult<T> {
        /**
         * 是否成功
         */
        @Builder.Default
        private Boolean success = true;

        /**
         * 执行结果数据
         */
        private T data;

        /**
         * 错误信息（如果失败）
         */
        private String error;

        /**
         * 执行耗时（毫秒）
         */
        private Long duration;

        /**
         * 是否命中缓存
         */
        @Builder.Default
        private Boolean cached = false;

        /**
         * 元数据（额外信息）
         */
        private Map<String, Object> metadata;

        /**
         * 工具调用 ID（用于追踪）
         */
        private String callId;

        /**
         * 创建成功结果
         */
        public static <T> ToolResult<T> success(T data, long duration, boolean cached) {
            return ToolResult.<T>builder()
                    .success(true)
                    .data(data)
                    .duration(duration)
                    .cached(cached)
                    .build();
        }

        /**
         * 创建失败结果
         */
        public static <T> ToolResult<T> failure(String error, long duration) {
            return ToolResult.<T>builder()
                    .success(false)
                    .error(error)
                    .duration(duration)
                    .build();
        }
    }

    /**
     * MCP 工具注解
     * <p>
     * 用于标记工具类，支持自动注册
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tool {
        /**
         * 工具名称
         */
        String name();

        /**
         * 工具描述
         */
        String description();

        /**
         * 工具分类
         */
        String category() default "general";

        /**
         * 是否启用缓存
         */
        boolean cacheable() default true;

        /**
         * 是否异步执行
         */
        boolean async() default false;
    }
}
