package com.aerofin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring AI ChatClient 配置
 * <p>
 * 使用 Deepseek 作为 LLM 提供商（通过 OpenAI 兼容 API）
 * <p>
 * 面试亮点：
 * 1. 使用 Deepseek R1 深度推理模型
 * 2. 统一配置 LLM 参数（temperature, max-tokens 等）
 * 3. 支持流式输出（SSE）
 * 4. 集成 Function Calling（工具调用）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private final AeroFinProperties properties;

    /**
     * 配置 ChatClient Bean（使用 Deepseek）
     * <p>
     * ChatClient 是 Spring AI 的核心接口，用于：
     * - 与 LLM 交互
     * - 管理对话上下文
     * - 处理工具调用（Function Calling）
     * - 支持流式输出
     */
    @Bean
    @Primary
    public ChatClient chatClient() {
        var deepseekConfig = properties.getLlm().getDeepseek();

        log.info("🚀 Initializing ChatClient with Deepseek");
        log.info("   Base URL: {}", deepseekConfig.getBaseUrl());
        log.info("   Model: {}", deepseekConfig.getModel());
        log.info("   Temperature: {}", deepseekConfig.getTemperature());
        log.info("   API Key: {}...{}",
                 deepseekConfig.getApiKey().substring(0, 7),
                 deepseekConfig.getApiKey().substring(deepseekConfig.getApiKey().length() - 4));

        // 使用 OpenAI API 兼容接口连接 Deepseek
        // 重要：baseUrl 必须以 /v1 结尾（OpenAI API 标准）
        String apiBaseUrl = deepseekConfig.getBaseUrl();

        log.info("   Full API URL: {}", apiBaseUrl);
        // 使用 SimpleClientHttpRequestFactory 来强制设置超时
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);  // 连接超时 5秒 (握手要快)
        requestFactory.setReadTimeout(120000);   // 读取超时 2分钟 (给它足够时间生成长文)

        OpenAiApi deepseekApi = new OpenAiApi(
                apiBaseUrl,
                deepseekConfig.getApiKey(),
                RestClient.builder().requestFactory(requestFactory),// <--- 注入超时配置
                WebClient.builder()
        );

        // 配置 OpenAiChatModel 的选项
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .withModel(deepseekConfig.getModel())
                .withTemperature(deepseekConfig.getTemperature())
                .withMaxTokens(deepseekConfig.getMaxTokens())
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(deepseekApi, defaultOptions);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(defaultOptions)
                .build();

        log.info("✅ ChatClient initialized successfully with Deepseek {}", deepseekConfig.getModel());

        return chatClient;
    }
}
