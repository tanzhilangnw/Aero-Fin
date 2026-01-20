package com.aerofin.tools;

import com.aerofin.config.AeroFinProperties;
import com.aerofin.aspect.ToolCacheContext;
import com.aerofin.cache.DistributedCacheManager;
import com.aerofin.exception.ToolTimeoutException;
import com.aerofin.mcp.tools.LoanCalculatorTool;
import com.aerofin.model.entity.Policy;
import com.aerofin.model.entity.WaiverApplication;
import com.aerofin.repository.PolicyRepository;
import com.aerofin.repository.WaiverApplicationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 金融工具类（模拟 MCP 工具调用）
 * <p>
 * 核心亮点：
 * 1. 使用 @Tool 注解（Spring AI Function Calling）
 * 2. 集成 Caffeine Cache 缓存耗时工具结果
 * 3. 支持异步执行 + 超时控制
 * 4. 使用 Thread.sleep() 模拟真实耗时操作
 * <p>
 * 面试要点：
 * - 工具调用缓存策略（基于参数 Hash）
 * - CompletableFuture 异步编排
 * - 超时保护机制
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialTools {

    private final PolicyRepository policyRepository;
    private final WaiverApplicationRepository waiverApplicationRepository;
    private final AeroFinProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DistributedCacheManager cacheManager;
    private final LoanCalculatorTool loanCalculatorTool;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 工具1: 等额本息贷款计算器
     * <p>
     * 关键亮点：
     * 1. 使用 Thread.sleep(500) 模拟耗时计算
     * 2. 结合 Caffeine Cache 缓存结果（基于参数 Hash）
     * 3. 如果缓存命中，直接返回；否则计算并缓存
     * <p>
     * 参数：
     *
     * @param principal   贷款本金（元）
     * @param annualRate  年利率（如 0.0385 表示 3.85%）
     * @param termMonths  贷款期限（月）
     * @return 每月还款额和总利息
     */
    public String calculateLoan(double principal, double annualRate, int termMonths) {
        try {
            // 迁移到 MCP 工具：LoanCalculatorTool（参数校验 + 结构化结果 + 内置缓存）
            LoanCalculatorTool.LoanInput input = LoanCalculatorTool.LoanInput.builder()
                    .principal(principal)
                    .annualRate(annualRate)
                    .termMonths(termMonths)
                    .repaymentType("EQUAL_INSTALLMENT")
                    .build();

            var toolResult = loanCalculatorTool.execute(input);

            // 将 MCP 缓存命中状态透传给 AOP 监控标签
            ToolCacheContext.markCacheHit(Boolean.TRUE.equals(toolResult.getCached()));

            if (Boolean.FALSE.equals(toolResult.getSuccess()) || toolResult.getData() == null) {
                return "贷款计算失败：" + (toolResult.getError() == null ? "未知错误" : toolResult.getError());
            }

            // 输出保持原有字符串形式，便于当前 Prompt 与前端直接展示
            return toolResult.getData().toFormattedString();
        } catch (Exception e) {
            ToolCacheContext.markCacheHit(false);
            log.error("calculateLoan (MCP) failed", e);
            return "贷款计算失败：" + e.getMessage();
        }
    }

    /**
     * 工具2: 查询金融政策
     * <p>
     * 支持：
     * 1. 按政策编码精确查询
     * 2. 按政策类型查询（LOAN/WAIVER/INTEREST）
     * 3. 按关键词模糊搜索
     * <p>
     * 面试亮点：
     * - 演示如何从 OceanBase 查询数据
     * - 支持多种查询模式
     * - 返回结构化结果
     *
     * @param queryType 查询类型: code/type/keyword
     * @param queryValue 查询值
     */
    public String queryPolicy(String queryType, String queryValue) {
        log.info("🔍 Querying policy: type={}, value={}", queryType, queryValue);

        try {
            switch (queryType.toLowerCase()) {
                case "code":
                    return policyRepository.findByPolicyCode(queryValue)
                            .map(this::formatPolicy)
                            .orElse("未找到政策编码为 " + queryValue + " 的政策");

                case "type":
                    List<Policy> policies = policyRepository.findByPolicyType(queryValue.toUpperCase());
                    if (policies.isEmpty()) {
                        return "未找到类型为 " + queryValue + " 的政策";
                    }
                    return formatPolicies(policies);

                case "keyword":
                    List<Policy> searchResults = policyRepository.searchByKeyword(queryValue);
                    if (searchResults.isEmpty()) {
                        return "未找到包含关键词 \"" + queryValue + "\" 的政策";
                    }
                    return formatPolicies(searchResults);

                default:
                    return "不支持的查询类型: " + queryType + "。支持的类型: code/type/keyword";
            }
        } catch (Exception e) {
            log.error("查询政策失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 工具3: 提交罚息减免申请
     * <p>
     * 面试亮点：
     * - 演示如何插入数据到 OceanBase
     * - 生成唯一申请编号（UUID）
     * - 返回申请结果
     *
     * @param userId        用户ID
     * @param loanAccount   贷款账号
     * @param waiverAmount  减免金额
     * @param reason        申请原因
     */
    public String applyWaiver(String userId, String loanAccount, double waiverAmount, String reason) {
        log.info("📝 Submitting waiver application: userId={}, loanAccount={}, amount={}",
                userId, loanAccount, waiverAmount);

        try {
            // 生成申请编号
            String applicationNo = "WAIVER-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 创建申请
            WaiverApplication application = WaiverApplication.builder()
                    .applicationNo(applicationNo)
                    .userId(userId)
                    .loanAccount(loanAccount)
                    .waiverAmount(BigDecimal.valueOf(waiverAmount))
                    .reason(reason)
                    .status("PENDING")
                    .submittedAt(LocalDateTime.now())
                    .build();

            // 保存到数据库
            waiverApplicationRepository.save(application);

            return String.format(
                    "罚息减免申请已提交成功！\n" +
                            "- 申请编号：%s\n" +
                            "- 贷款账号：%s\n" +
                            "- 减免金额：%.2f 元\n" +
                            "- 申请状态：待审核\n" +
                            "- 提交时间：%s\n\n" +
                            "请保留申请编号，我们将在 3-5 个工作日内完成审核。",
                    applicationNo, loanAccount, waiverAmount,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
        } catch (Exception e) {
            log.error("提交罚息减免申请失败", e);
            return "申请提交失败: " + e.getMessage();
        }
    }

    /**
     * 工具4: 查询减免申请状态
     *
     * @param applicationNo 申请编号
     */
    public String queryWaiverStatus(String applicationNo) {
        log.info("🔍 Querying waiver status: {}", applicationNo);

        return waiverApplicationRepository.findByApplicationNo(applicationNo)
                .map(app -> String.format(
                        "罚息减免申请详情：\n" +
                                "- 申请编号：%s\n" +
                                "- 贷款账号：%s\n" +
                                "- 减免金额：%.2f 元\n" +
                                "- 申请状态：%s\n" +
                                "- 提交时间：%s\n" +
                                (app.getReviewedAt() != null ? "- 审核时间：" + app.getReviewedAt() + "\n" : "") +
                                (app.getReviewComment() != null ? "- 审核意见：" + app.getReviewComment() : ""),
                        app.getApplicationNo(),
                        app.getLoanAccount(),
                        app.getWaiverAmount(),
                        getStatusText(app.getStatus()),
                        app.getSubmittedAt()
                ))
                .orElse("未找到申请编号为 " + applicationNo + " 的记录");
    }

    /**
     * 异步执行工具（支持超时控制）
     * <p>
     * 面试亮点：
     * - CompletableFuture 异步编排
     * - 超时保护机制
     */
    public <T> T executeWithTimeout(Supplier<T> task, String toolName) {
        long timeout = properties.getTools().getTimeoutMillis();

        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(task, executorService);
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("Tool execution timeout: {}", toolName);
            throw new ToolTimeoutException(toolName, timeout);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Tool execution failed: {}", toolName, e);
            throw new RuntimeException("Tool execution failed: " + toolName, e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成缓存 Key（基于参数 Hash）
     */
    private String generateCacheKey(String toolName, Object... params) {
        StringBuilder sb = new StringBuilder(toolName);
        for (Object param : params) {
            sb.append(":").append(param);
        }
        return Hashing.sha256()
                .hashString(sb.toString(), StandardCharsets.UTF_8)
                .toString()
                .substring(0, 16);
    }

    /**
     * 格式化单个政策
     */
    private String formatPolicy(Policy policy) {
        return String.format(
                "政策详情：\n" +
                        "- 政策编码：%s\n" +
                        "- 政策名称：%s\n" +
                        "- 政策类型：%s\n" +
                        "- 政策描述：%s\n" +
                        "- 详细内容：%s\n" +
                        "- 生效日期：%s",
                policy.getPolicyCode(),
                policy.getPolicyName(),
                getPolicyTypeText(policy.getPolicyType()),
                policy.getDescription(),
                policy.getContent(),
                policy.getEffectiveDate()
        );
    }

    /**
     * 格式化政策列表
     */
    private String formatPolicies(List<Policy> policies) {
        if (policies.isEmpty()) {
            return "未找到相关政策";
        }

        StringBuilder sb = new StringBuilder("找到以下政策：\n\n");
        for (int i = 0; i < policies.size(); i++) {
            Policy p = policies.get(i);
            sb.append(String.format("%d. %s (%s)\n   %s\n\n",
                    i + 1, p.getPolicyName(), p.getPolicyCode(), p.getDescription()));
        }
        return sb.toString();
    }

    private String getPolicyTypeText(String type) {
        return switch (type) {
            case "LOAN" -> "贷款政策";
            case "WAIVER" -> "罚息减免政策";
            case "INTEREST" -> "利息优惠政策";
            default -> type;
        };
    }

    private String getStatusText(String status) {
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已批准";
            case "REJECTED" -> "已拒绝";
            default -> status;
        };
    }
}
