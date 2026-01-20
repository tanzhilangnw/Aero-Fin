package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 反思/审阅 Agent
 *
 * 设计目标：
 * - 对其他 Agent 给出的初稿回答进行二次审阅（Reflect）
 * - 重点检查：合规性、风险提示是否充分、是否有明显逻辑问题
 * - 在必要时给出修订版答案，或附加一段“风险提示/补充说明”
 *
 * 使用方式（推荐模式）：
 * - 步骤 1：业务 Agent 生成初步回答（draftAnswer）
 * - 步骤 2：MultiAgentOrchestrator 构造包含 userMessage + draftAnswer 的 AgentMessage 发给 ReflectAgent
 * - 步骤 3：ReflectAgent 返回 revisedAnswer（可能是“通过 + 补充说明”或“修订版完整答案”）
 */
@Slf4j
@Component
public class ReflectAgent extends BaseAgent {

    public ReflectAgent(ChatClient chatClient) {
        super(AgentRole.REFLECTOR, chatClient);
        log.info("[反思专家Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[反思专家] 审阅其他Agent回答，sessionId={}, parentMessageId={}",
                    message.getSessionId(), message.getParentMessageId());

            String userQuestion = message.getData("userQuestion", String.class);
            String draftAnswer = message.getData("draftAnswer", String.class);
            String sourceAgent = message.getData("sourceAgent", String.class);

            String prompt = buildReflectPrompt(userQuestion, draftAnswer, sourceAgent);

            String reflected = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(prompt)
                    .call()
                    .content();

            return message.createResponse(reflected);
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[反思专家] 处理流式审阅任务: sessionId={}", message.getSessionId());

        String userQuestion = message.getData("userQuestion", String.class);
        String draftAnswer = message.getData("draftAnswer", String.class);
        String sourceAgent = message.getData("sourceAgent", String.class);

        String prompt = buildReflectPrompt(userQuestion, draftAnswer, sourceAgent);

        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.REFLECTOR_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        // 反思 Agent 目前走纯语言审阅，不直接调用业务工具
        return List.of();
    }

    /**
     * 构建反思 Prompt：包含用户原始问题、来源 Agent 以及初稿回答
     */
    private String buildReflectPrompt(String userQuestion, String draftAnswer, String sourceAgent) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户原始问题】\n")
          .append(userQuestion == null ? "（未知）" : userQuestion)
          .append("\n\n");

        sb.append("【来源Agent】\n")
          .append(sourceAgent == null ? "UNKNOWN" : sourceAgent)
          .append("\n\n");

        sb.append("【初稿回答】\n")
          .append(draftAnswer == null ? "（无内容）" : draftAnswer)
          .append("\n\n");

        sb.append("请根据系统提示词，对上述初稿进行审阅与必要的修订。");
        return sb.toString();
    }
}


