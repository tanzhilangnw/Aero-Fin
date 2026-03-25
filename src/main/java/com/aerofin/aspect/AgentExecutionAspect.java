package com.aerofin.aspect;

import com.aerofin.agent.BaseAgent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Agent 执行监控切面
 * <p>
 * 核心功能：
 * 1. 拦截所有 Agent 的 execute 和 executeStream 方法
 * 2. 记录每个 Agent 的执行耗时
 * 3. 上报 Prometheus 监控指标
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentExecutionAspect {

    private final MeterRegistry meterRegistry;

    /**
     * 环绕通知：拦截 BaseAgent 子类中的所有公共方法
     */
    @Around("execution(public * com.aerofin.agent.BaseAgent+.*(..))")
    public Object monitorAgentExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        BaseAgent agent = (BaseAgent) joinPoint.getTarget();
        String agentRole = agent.getRole().name();
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";

        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            status = "FAILURE";
            log.error("Agent {} execution failed", agentRole, e);
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            recordMetrics(agentRole, status, executionTime);
        }
    }

    private void recordMetrics(String agentRole, String status, long executionTime) {
        try {
            // Agent调用总次数
            meterRegistry.counter("aerofin.agent.invocations",
                "agent_role", agentRole,
                "status", status
            ).increment();

            // Agent调用耗时
            Timer.builder("aerofin.agent.execution.time")
                .tag("agent_role", agentRole)
                .tag("status", status)
                .description("Agent execution time in milliseconds")
                .register(meterRegistry)
                .record(executionTime, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Failed to record agent metrics for {}", agentRole, e);
        }
    }
}
