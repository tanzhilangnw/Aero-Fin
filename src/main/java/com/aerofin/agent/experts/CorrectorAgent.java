package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CorrectorAgent extends BaseAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SUGGESTION_PATTERN =
            Pattern.compile("\"suggestion\"\\s*:\\s*\"(.*?)\"");

    public CorrectorAgent(ChatClient chatClient, ApplicationEventPublisher eventPublisher) {
        super(AgentRole.CORRECTOR, chatClient, eventPublisher);
        log.info("[CorrectorAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String originalQuery  = message.getData("originalQuery",  String.class);
            String failedAgent    = message.getData("failedAgent",    String.class);
            String failureReason  = message.getData("failureReason",  String.class);

            String analysisPrompt = String.format(
                "一个AI Agent任务执行失败，请分析原因并提供修正计划。\n\n"
                + "原始用户请求: \"%s\"\n失败的Agent: %s\n失败原因: \"%s\"\n\n"
                + "以JSON格式返回，包含 'analysis' 和 'suggestion' 字段。",
                originalQuery, failedAgent, failureReason);

            String llmResponse = chatClient.prompt().user(analysisPrompt).call().content();
            String suggestion = extractSuggestion(llmResponse);

            if (suggestion == null || suggestion.isBlank()) {
                return message.createResponse("无法生成修正建议。");
            }

            AgentRole targetRole = AgentRole.valueOf(failedAgent);
            AgentMessage rerouteMessage = AgentMessage.createTaskAssignment(
                    AgentRole.CORRECTOR, targetRole, suggestion, message.getSessionId());
            rerouteMessage.addData("originalQuery", originalQuery);
            rerouteMessage.addData("isCorrection", true);

            AgentMessage response = message.createResponse("修正计划已生成。");
            response.addData("rerouteMessage", rerouteMessage);
            response.addData("suggestedQuery", suggestion);
            return response;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        return Flux.empty(); // Corrector does not stream
    }

    @Override
    protected String getSystemPrompt() { return ""; }

    @Override
    protected List<String> getAvailableTools() { return List.of(); }

    private String extractSuggestion(String llmResponse) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = MAPPER.readValue(llmResponse, Map.class);
            return map.get("suggestion");
        } catch (Exception e) {
            log.warn("[CorrectorAgent] JSON parse failed, falling back to regex");
            Matcher m = SUGGESTION_PATTERN.matcher(llmResponse);
            return m.find() ? m.group(1) : null;
        }
    }
}
