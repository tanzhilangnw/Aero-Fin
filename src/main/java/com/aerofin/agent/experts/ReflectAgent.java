package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class ReflectAgent extends BaseAgent {

    public ReflectAgent(ChatClient chatClient, ApplicationEventPublisher eventPublisher) {
        super(AgentRole.REFLECTOR, chatClient, eventPublisher);
        log.info("[ReflectAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String prompt = buildReflectPrompt(
                    message.getData("userQuestion", String.class),
                    message.getData("draftAnswer", String.class),
                    message.getData("sourceAgent", String.class));
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
        String prompt = buildReflectPrompt(
                message.getData("userQuestion", String.class),
                message.getData("draftAnswer", String.class),
                message.getData("sourceAgent", String.class));
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.REFLECTOR_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of(); }

    private String buildReflectPrompt(String userQuestion, String draftAnswer, String sourceAgent) {
        return "【用户原始问题】\n" + (userQuestion == null ? "（未知）" : userQuestion) + "\n\n"
             + "【来源Agent】\n" + (sourceAgent == null ? "UNKNOWN" : sourceAgent) + "\n\n"
             + "【初稿回答】\n" + (draftAnswer == null ? "（无内容）" : draftAnswer) + "\n\n"
             + "请根据系统提示词，对上述初稿进行审阅与必要的修订。";
    }
}
