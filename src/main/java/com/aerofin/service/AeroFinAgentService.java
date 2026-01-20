package com.aerofin.service;

import com.aerofin.tools.FinancialTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Aero-Fin Agent 核心服务
 * <p>
 * 核心功能：
 * 1. 流式对话（SSE 打字机效果）
 * 2. ReAct 模式（Thought → Action → Observation）
 * 3. 工具调用（Function Calling）
 * 4. RAG 检索增强
 * 5. 自我修正能力
 * 6. 多轮上下文管理
 * <p>
 * 面试亮点：
 * - Spring AI ChatClient 流式输出
 * - ReAct Prompt Engineering
 * - Function Calling 工具编排
 * - RAG 模式实现
 * - 自我修正策略（检索重试）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AeroFinAgentService {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final VectorSearchService vectorSearchService;
    private final FinancialTools financialTools;

    /**
     * System Prompt（ReAct 模式）
     * <p>
     * 核心设计：
     * 1. 角色定位：金融信贷智能客服
     * 2. ReAct 思考链：Thought → Action → Observation → Answer
     * 3. 工具调用指导：何时使用哪个工具
     * 4. 自我修正机制：检索失败时重试
     * 5. 回复规范：友好、专业、结构化
     * <p>
     * 面试要点：
     * - Prompt Engineering 最佳实践
     * - Few-Shot Learning 示例
     * - 工具调用规范
     */
    private static final String SYSTEM_PROMPT = """
            你是 Aero-Fin 的智能金融客服助手，专门帮助用户解答贷款、罚息减免、利息优惠等金融问题。

            # 核心能力
            你拥有以下工具，请根据用户问题**自主选择**合适的工具：

            1. **calculateLoan** - 等额本息贷款计算器
               - 参数：principal(本金), annualRate(年利率), termMonths(期限月数)
               - 用途：计算月还款额、总利息
               - 示例：用户问"20万贷款36个月，利率4.5%，每月还多少？"

            2. **queryPolicy** - 查询金融政策
               - 参数：queryType(查询类型: code/type/keyword), queryValue(查询值)
               - 用途：查询贷款政策、减免政策、利息政策
               - 示例：用户问"有哪些小微企业贷款政策？" → queryType="keyword", queryValue="小微企业"

            3. **applyWaiver** - 提交罚息减免申请
               - 参数：userId, loanAccount, waiverAmount, reason
               - 用途：帮用户提交减免申请
               - 示例：用户说"我想申请减免500元罚息"

            4. **queryWaiverStatus** - 查询减免申请状态
               - 参数：applicationNo(申请编号)
               - 用途：查询申请审核状态

            # 工作流程（ReAct 模式）
            请遵循以下思考链：

            **Thought（思考）**：分析用户问题，判断需要哪些工具
            **Action（行动）**：调用工具获取信息
            **Observation（观察）**：分析工具返回结果
            **Answer（回答）**：基于观察结果，给出专业回复

            # 自我修正机制 ⚠️
            如果工具调用返回"未找到"或空结果：
            1. **不要直接放弃**，尝试换关键词重试
            2. 例如："未找到'企业贷款'" → 尝试 "经营贷" 或 "小微企业"
            3. 最多重试 2 次，仍未找到再告知用户

            # 回复规范
            1. **友好专业**：使用礼貌用语，避免机器人口吻
            2. **结构化**：使用分点、换行，便于阅读
            3. **准确性**：基于工具返回的真实数据，不要编造
            4. **引导性**：主动询问用户需求，提供建议

            # 示例对话

            **用户**：我想贷款20万，3年还清，利率4.5%，每月还多少？
            **Thought**：用户需要计算等额本息，使用 calculateLoan 工具
            **Action**：调用 calculateLoan(200000, 0.045, 36)
            **Observation**：月还款额 5923.45 元，总利息 13244.20 元
            **Answer**：
            根据您的贷款需求计算：
            - 贷款本金：20万元
            - 年利率：4.5%
            - 贷款期限：36个月

            💰 **每月还款额：5,923.45 元**
            📊 总还款额：213,244.20 元
            📈 总利息：13,244.20 元

            这个方案符合您的预算吗？需要我帮您查询其他期限的还款计划吗？

            ---

            现在，请根据用户的问题，运用上述能力和流程，提供专业的金融咨询服务。
            """;

    /**
     * 处理用户消息（流式输出）
     * <p>
     * 核心流程：
     * 1. 检索相关政策（RAG）
     * 2. 加载会话历史
     * 3. 构建完整 Prompt（System + Context + History + User）
     * 4. 流式调用 LLM（SSE）
     * 5. 保存会话记录
     * <p>
     * 面试亮点：
     * - Flux 响应式流式输出
     * - RAG 上下文注入
     * - 工具调用自动执行（Function Calling）
     * - 会话上下文管理
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param userMessage 用户消息
     * @return 流式响应（Flux<String>）
     */
    public Flux<String> chatStream(String sessionId, String userId, String userMessage) {
        log.info("Processing chat request: sessionId={}, userId={}, message={}",
                sessionId, userId, truncate(userMessage, 100));

        // 1. 保存用户消息到会话历史
        conversationService.saveUserMessage(sessionId, userId, userMessage);

        // 2. 向量检索相关政策（RAG）
        String ragContext = retrieveRelevantContext(userMessage);

        // 3. 加载会话历史（滑动窗口）
        List<Message> conversationHistory = conversationService.getConversationHistory(sessionId);

        // 4. 构建增强 Prompt（System + RAG + History）
        String enhancedSystemPrompt = SYSTEM_PROMPT + "\n\n" +
                "# 检索到的相关政策信息\n" + ragContext + "\n" +
                "请结合以上政策信息回答用户问题。";

        // 5. 流式调用 ChatClient
        StringBuilder assistantResponse = new StringBuilder();

        return chatClient.prompt()
                .system(enhancedSystemPrompt)
                .messages(conversationHistory)
                .user(userMessage)
                .functions(
                        "calculateLoan", "queryPolicy", "applyWaiver", "queryWaiverStatus"
                )
                .stream()
                .content()
                .doOnNext(chunk -> {
                    // 收集完整响应（用于保存到数据库）
                    assistantResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式输出完成后，保存 Agent 回复到会话历史
                    String fullResponse = assistantResponse.toString();
                    conversationService.saveAssistantMessage(sessionId, userId, fullResponse, null);

                    log.info("Chat stream completed: sessionId={}, responseLength={}",
                            sessionId, fullResponse.length());

                    // 清理旧消息（如果超过最大历史数量）
                    conversationService.cleanupOldMessages(sessionId);
                })
                .doOnError(error -> {
                    log.error("Chat stream error: sessionId={}", sessionId, error);
                })
                .onErrorResume(error -> {
                    // 错误降级：返回友好提示
                    String errorMessage = "抱歉，处理您的请求时遇到问题：" + error.getMessage() +
                            "\n\n请稍后重试，或换个方式描述您的问题。";
                    return Flux.just(errorMessage);
                });
    }

    /**
     * 非流式对话（用于测试）
     */
    public String chat(String sessionId, String userId, String userMessage) {
        conversationService.saveUserMessage(sessionId, userId, userMessage);

        String ragContext = retrieveRelevantContext(userMessage);
        List<Message> conversationHistory = conversationService.getConversationHistory(sessionId);

        String enhancedSystemPrompt = SYSTEM_PROMPT + "\n\n" +
                "# 检索到的相关政策信息\n" + ragContext;

        String response = chatClient.prompt()
                .system(enhancedSystemPrompt)
                .messages(conversationHistory)
                .user(userMessage)
                .functions(
                        "calculateLoan", "queryPolicy", "applyWaiver", "queryWaiverStatus"
                )
                .call()
                .content();

        conversationService.saveAssistantMessage(sessionId, userId, response, null);

        return response;
    }

    /**
     * RAG 检索增强
     * <p>
     * 流程：
     * 1. 向量化用户问题
     * 2. 在 Milvus 中检索相似政策文档
     * 3. 格式化为上下文，注入 Prompt
     * <p>
     * 面试亮点：
     * - 语义检索（非关键词匹配）
     * - 上下文增强（Context Enhancement）
     * - 检索失败降级处理
     */
    private String retrieveRelevantContext(String userMessage) {
        try {
            List<Document> relevantDocs = vectorSearchService.searchRelevantPolicies(userMessage);
            String context = vectorSearchService.formatRetrievedContext(relevantDocs);

            log.info("RAG context retrieved: {} documents", relevantDocs.size());
            return context;

        } catch (Exception e) {
            log.warn("Failed to retrieve RAG context, continuing without it", e);
            return "暂无相关政策信息。";
        }
    }

    /**
     * 截断字符串（日志用）
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
