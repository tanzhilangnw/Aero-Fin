package com.aerofin.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent基类
 * <p>
 * 所有专家Agent的抽象基类，提供：
 * 1. 通用的消息处理接口
 * 2. Agent状态管理
 * 3. 流式响应支持
 * 4. 性能指标记录
 * <p>
 * 面试亮点：
 * - 模板方法模式（定义Agent执行流程）
 * - 响应式编程（Reactor）
 * - Agent状态机管理
 *
 * @author Aero-Fin Team
 */
@Slf4j
public abstract class BaseAgent {

    /**
     * Agent角色
     */
    protected final AgentRole role;

    /**
     * ChatClient（用于AI推理）
     */
    protected final ChatClient chatClient;

    /**
     * Agent状态
     */
    protected volatile AgentState state = AgentState.IDLE;

    /**
     * 性能指标
     */
    protected final ConcurrentMap<String, Long> metrics = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    protected BaseAgent(AgentRole role, ChatClient chatClient) {
        this.role = role;
        this.chatClient = chatClient;
        initializeMetrics();
    }

    /**
     * Agent状态枚举
     */
    public enum AgentState {
        IDLE,           // 空闲
        PROCESSING,     // 处理中
        WAITING,        // 等待协作
        ERROR,          // 错误
        COMPLETED       // 已完成
    }

    // ==================== 核心方法（子类必须实现）====================

    /**
     * 处理消息（非流式）
     * <p>
     * 子类实现具体的业务逻辑
     *
     * @param message Agent消息
     * @return 处理结果
     */
    public abstract Mono<AgentMessage> handleMessage(AgentMessage message);

    /**
     * 处理消息（流式）
     * <p>
     * 用于流式输出场景
     *
     * @param message Agent消息
     * @return 流式响应
     */
    public abstract Flux<String> handleMessageStream(AgentMessage message);

    /**
     * 获取Agent的系统提示词
     * <p>
     * 定义Agent的专业领域和行为准则
     *
     * @return 系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 获取可用的工具列表
     * <p>
     * 每个Agent可以有不同的工具集
     *
     * @return 工具名称列表
     */
    protected abstract List<String> getAvailableTools();

    // ==================== 模板方法（定义执行流程）====================

    /**
     * 执行任务（模板方法）
     * <p>
     * 定义标准的任务执行流程：
     * 1. 预处理（验证、日志）
     * 2. 执行核心逻辑
     * 3. 后处理（指标记录、状态更新）
     */
    public Mono<AgentMessage> execute(AgentMessage message) {
        return Mono.defer(() -> {
            // 1. 预处理
            preProcess(message);

            // 2. 执行核心逻辑
            return handleMessage(message)
                    .doOnNext(result -> {
                        // 3. 后处理
                        postProcess(message, result);
                    })
                    .doOnError(error -> {
                        // 错误处理
                        handleError(message, error);
                    });
        });
    }

    /**
     * 执行任务（流式）
     */
    public Flux<String> executeStream(AgentMessage message) {
        return Flux.defer(() -> {
            // 1. 预处理
            preProcess(message);

            // 2. 执行核心逻辑
            return handleMessageStream(message)
                    .doOnComplete(() -> {
                        // 3. 后处理
                        postProcess(message, null);
                    })
                    .doOnError(error -> {
                        // 错误处理
                        handleError(message, error);
                    });
        });
    }

    // ==================== 生命周期方法 ====================

    /**
     * 预处理
     */
    protected void preProcess(AgentMessage message) {
        log.info("[{}] 🚀 Received message: {} from {}",
                role.getName(), message.getMessageId(), message.getSender().getName());

        // 更新状态
        setState(AgentState.PROCESSING);

        // 记录开始时间
        metrics.put("lastStartTime", System.currentTimeMillis());

        // 增加处理计数
        metrics.merge("totalProcessed", 1L, Long::sum);
    }

    /**
     * 后处理
     */
    protected void postProcess(AgentMessage message, AgentMessage result) {
        long duration = System.currentTimeMillis() - metrics.get("lastStartTime");

        log.info("[{}] ✅ Completed message: {} in {}ms",
                role.getName(), message.getMessageId(), duration);

        // 更新状态
        setState(AgentState.IDLE);

        // 记录响应时间
        metrics.merge("totalResponseTime", duration, Long::sum);
        metrics.put("lastResponseTime", duration);
    }

    /**
     * 错误处理
     */
    protected void handleError(AgentMessage message, Throwable error) {
        log.error("[{}] ❌ Error processing message: {}",
                role.getName(), message.getMessageId(), error);

        // 更新状态
        setState(AgentState.ERROR);

        // 记录错误计数
        metrics.merge("totalErrors", 1L, Long::sum);
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断Agent是否能处理该消息
     */
    public boolean canHandle(AgentMessage message) {
        return message.getReceiver() == this.role;
    }

    /**
     * 获取Agent角色
     */
    public AgentRole getRole() {
        return role;
    }

    /**
     * 获取Agent状态
     */
    public AgentState getState() {
        return state;
    }

    /**
     * 设置Agent状态
     */
    protected void setState(AgentState newState) {
        log.debug("[{}] State changed: {} -> {}", role.getName(), state, newState);
        this.state = newState;
    }

    /**
     * 判断Agent是否空闲
     */
    public boolean isIdle() {
        return state == AgentState.IDLE;
    }

    /**
     * 获取性能指标
     */
    public ConcurrentMap<String, Long> getMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    /**
     * 获取平均响应时间
     */
    public long getAverageResponseTime() {
        Long totalTime = metrics.get("totalResponseTime");
        Long totalProcessed = metrics.get("totalProcessed");

        if (totalTime == null || totalProcessed == null || totalProcessed == 0) {
            return 0L;
        }

        return totalTime / totalProcessed;
    }

    /**
     * 初始化指标
     */
    private void initializeMetrics() {
        metrics.put("totalProcessed", 0L);
        metrics.put("totalResponseTime", 0L);
        metrics.put("totalErrors", 0L);
        metrics.put("lastStartTime", 0L);
        metrics.put("lastResponseTime", 0L);
    }

    /**
     * 重置指标
     */
    public void resetMetrics() {
        initializeMetrics();
        log.info("[{}] Metrics reset", role.getName());
    }
}
