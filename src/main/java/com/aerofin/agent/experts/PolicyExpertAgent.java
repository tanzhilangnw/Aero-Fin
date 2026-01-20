package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.service.VectorSearchService;
import com.aerofin.tools.FinancialTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 政策专家Agent
 * <p>
 * 专业领域：
 * 1. 政策查询（RAG向量检索）
 * 2. 政策解读和说明
 * 3. 优惠活动推荐
 * 4. 申请条件解析
 * <p>
 * 面试亮点：
 * - RAG检索增强生成
 * - 向量相似度搜索
 * - 上下文增强推理
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class PolicyExpertAgent extends BaseAgent {

    private final FinancialTools financialTools;
    private final VectorSearchService vectorSearchService;

    public PolicyExpertAgent(
            ChatClient chatClient,
            FinancialTools financialTools,
            VectorSearchService vectorSearchService) {
        super(AgentRole.POLICY_EXPERT, chatClient);
        this.financialTools = financialTools;
        this.vectorSearchService = vectorSearchService;
        log.info("[政策专家Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[政策专家] 处理任务: {}", message.getContent());

            // 1. 先进行RAG检索，获取相关政策
            List<Document> relevantPolicies = vectorSearchService
                    .searchRelevantPolicies(message.getContent());

            // 2. 构建增强提示词（注入检索到的政策）
            String prompt = buildPromptWithContext(message, relevantPolicies);

            // 3. 调用ChatClient进行推理
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(prompt)
                    .functions("queryPolicy") // 注册政策查询工具
                    .call()
                    .content();

            // 4. 返回结果
            AgentMessage result = message.createResponse(response);
            result.addData("retrievedPolicies", relevantPolicies.size());
            return result;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[政策专家] 处理流式任务: {}", message.getContent());

        // 1. RAG检索
        List<Document> relevantPolicies = vectorSearchService
                .searchRelevantPolicies(message.getContent());

        // 2. 构建增强提示词
        String prompt = buildPromptWithContext(message, relevantPolicies);

        // 3. 流式输出
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .functions("queryPolicy")
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.POLICY_RAG_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of("queryPolicy");
    }

    /**
     * 构建带上下文的提示词（RAG增强）
     */
    private String buildPromptWithContext(AgentMessage message, List<Document> relevantPolicies) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题: ").append(message.getContent()).append("\n\n");

        // 添加检索到的政策上下文
        if (!relevantPolicies.isEmpty()) {
            prompt.append("--- 检索到的相关政策 ---\n");
            for (int i = 0; i < relevantPolicies.size(); i++) {
                Document doc = relevantPolicies.get(i);
                prompt.append(String.format("[政策 %d]\n", i + 1));
                prompt.append(doc.getContent()).append("\n");
                prompt.append("相似度: ").append(doc.getMetadata().get("distance")).append("\n\n");
            }
            prompt.append("--- 检索结束 ---\n\n");
        } else {
            prompt.append("[注意] 向量检索未找到相关政策，可能需要使用 queryPolicy 工具进行数据库查询。\n\n");
        }

        // 添加用户提供的额外数据
        if (message.getData() != null && !message.getData().isEmpty()) {
            prompt.append("补充信息:\n");
            message.getData().forEach((key, value) ->
                    prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        return prompt.toString();
    }
}
