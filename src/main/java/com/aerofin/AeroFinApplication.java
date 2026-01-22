package com.aerofin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Aero-Fin 应用启动类
 * <p>
 * 流式输出的金融信贷智能客服系统
 * <p>
 * 核心技术栈：
 * - Java 21
 * - Spring Boot 3.4
 * - Spring AI (OpenAI)
 * - Spring WebFlux (响应式编程)
 * - Milvus (向量数据库)
 * - OceanBase/MySQL (关系型数据库)
 * - Caffeine Cache (多级缓存)
 * <p>
 * 核心亮点：
 * 1. SSE 流式输出（打字机效果）
 * 2. ReAct 模式（Thought → Action → Observation）
 * 3. Function Calling 工具调用
 * 4. RAG 检索增强生成
 * 5. 多轮对话上下文管理（滑动窗口）
 * 6. 自我修正能力（检索重试）
 * 7. 多级缓存策略（Caffeine + 布隆过滤器）
 * 8. AOP 工具调用监控
 * 9. Prometheus 监控指标
 *
 * @author Aero-Fin Team
 */
@Slf4j
@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreAutoConfiguration.class,
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
public class AeroFinApplication {

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext context = SpringApplication.run(AeroFinApplication.class, args);
        Environment env = context.getEnvironment();

        logApplicationStartup(env);
    }

    /**
     * 打印启动信息
     */
    private static void logApplicationStartup(Environment env) throws UnknownHostException {
        String protocol = env.getProperty("server.ssl.key-store") != null ? "https" : "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        log.info("""

                ----------------------------------------------------------
                    Application '{}' is running! Access URLs:
                    Local:      {}://localhost:{}{}
                    External:   {}://{}:{}{}
                    Profile(s): {}

                    API Endpoints:
                    - Chat Stream (SSE):    GET  /api/chat/stream?message=你好
                    - Chat (Non-Stream):    POST /api/chat
                    - Create Session:       POST /api/chat/session
                    - Health Check:         GET  /api/chat/health
                    - Actuator Metrics:     GET  /actuator/metrics
                    - Prometheus Metrics:   GET  /actuator/prometheus

                    Swagger UI (if enabled): {}/swagger-ui.html
                ----------------------------------------------------------
                """,
                env.getProperty("spring.application.name"),
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                env.getActiveProfiles().length > 0 ? String.join(", ", env.getActiveProfiles()) : "default",
                protocol + "://" + hostAddress + ":" + serverPort
        );
    }
}
