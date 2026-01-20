package com.aerofin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置
 * <p>
 * 配置 OpenAI Chat Model 和 ChatClient
 * <p>
 * 面试亮点：
 * 1. 统一配置 LLM 参数（temperature, max-tokens 等）
 * 2. 支持流式输出（SSE）
 * 3. 集成 Function Calling（工具调用）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private final AeroFinProperties properties;
    private final OpenAiChatModel openAiChatModel;

    /**
     * 配置 ChatClient Bean
     * <p>
     * ChatClient 是 Spring AI 的核心接口，用于：
     * - 与 LLM 交互
     * - 管理对话上下文
     * - 处理工具调用（Function Calling）
     * - 支持流式输出
     */
    @Bean
    public ChatClient chatClient() {
        var llmConfig = properties.getLlm();

        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .withModel(llmConfig.getModel())
                        .withTemperature(llmConfig.getTemperature())
                        .withMaxTokens(llmConfig.getMaxTokens())
                        .build())
                .build();

        log.info("Initialized ChatClient with model: {}, temperature: {}, maxTokens: {}",
                llmConfig.getModel(), llmConfig.getTemperature(), llmConfig.getMaxTokens());

        return chatClient;
    }
}
