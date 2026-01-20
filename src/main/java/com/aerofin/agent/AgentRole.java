package com.aerofin.agent;

import lombok.Getter;

/**
 * Agent 角色枚举
 * <p>
 * 多Agent协作架构中的角色定义：
 * 1. COORDINATOR - 协调器（任务分发、结果聚合）
 * 2. LOAN_EXPERT - 贷款专家（贷款计算、还款咨询）
 * 3. POLICY_EXPERT - 政策专家（政策查询、RAG检索）
     * 4. RISK_ASSESSMENT - 风控专家（风险评估、资格审核）
     * 5. CUSTOMER_SERVICE - 客服专家（投诉处理、罚息减免）
     * 6. REFLECTOR - 反思专家（答案审阅与风险合规检查）
 * <p>
 * 面试亮点：
 * - 多Agent协作架构
 * - 领域专家分工
 * - 任务路由与编排
 *
 * @author Aero-Fin Team
 */
@Getter
public enum AgentRole {

    /**
     * 协调器Agent
     * <p>
     * 职责：
     * - 意图识别与任务分类
     * - 路由请求到专家Agent
     * - 聚合多个Agent的结果
     * - 处理复杂的多步骤任务
     */
    COORDINATOR(
            "协调器",
            "负责任务分发和结果聚合",
            1.0
    ),

    /**
     * 贷款专家Agent
     * <p>
     * 职责：
     * - 贷款计算（月供、利息）
     * - 还款方式咨询
     * - 提前还款计算
     * - 贷款产品推荐
     */
    LOAN_EXPERT(
            "贷款专家",
            "专注于贷款计算和还款咨询",
            0.9
    ),

    /**
     * 政策专家Agent
     * <p>
     * 职责：
     * - 政策查询（RAG检索）
     * - 政策解读
     * - 优惠活动推荐
     * - 申请条件说明
     */
    POLICY_EXPERT(
            "政策专家",
            "专注于政策查询和解读",
            0.85
    ),

    /**
     * 风控专家Agent
     * <p>
     * 职责：
     * - 用户风险评估
     * - 贷款资格审核
     * - 额度预估
     * - 反欺诈检测
     */
    RISK_ASSESSMENT(
            "风控专家",
            "专注于风险评估和资格审核",
            0.95
    ),

    /**
     * 客服专家Agent
     * <p>
     * 职责：
     * - 投诉处理
     * - 罚息减免申请
     * - 账户查询
     * - 常见问题解答
     */
    CUSTOMER_SERVICE(
            "客服专家",
            "专注于客户服务和投诉处理",
            0.8
    ),

    /**
     * 反思/审阅Agent
     * <p>
     * 职责：
     * - 对其他Agent生成的回答进行二次审阅
     * - 检查是否存在合规/风险问题
     * - 在需要时给出修改建议或修订版答案
     */
    REFLECTOR(
            "反思专家",
            "负责对其他Agent的回答进行审阅和风险合规检查",
            0.9
    );

    /**
     * 角色名称
     */
    private final String name;

    /**
     * 角色描述
     */
    private final String description;

    /**
     * 专业度权重（用于结果聚合时的加权）
     */
    private final Double expertiseWeight;

    AgentRole(String name, String description, Double expertiseWeight) {
        this.name = name;
        this.description = description;
        this.expertiseWeight = expertiseWeight;
    }

    /**
     * 根据用户意图推断最合适的Agent角色
     *
     * @param userIntent 用户意图描述
     * @return 推荐的Agent角色
     */
    public static AgentRole inferFromIntent(String userIntent) {
        String intent = userIntent.toLowerCase();

        // 贷款计算相关
        if (intent.contains("贷款") || intent.contains("月供") || intent.contains("利率") ||
                intent.contains("还款") || intent.contains("本金") || intent.contains("利息")) {
            return LOAN_EXPERT;
        }

        // 政策查询相关
        if (intent.contains("政策") || intent.contains("优惠") || intent.contains("条件") ||
                intent.contains("申请") || intent.contains("资格")) {
            return POLICY_EXPERT;
        }

        // 风控评估相关
        if (intent.contains("额度") || intent.contains("审批") || intent.contains("征信") ||
                intent.contains("风险") || intent.contains("能否")) {
            return RISK_ASSESSMENT;
        }

        // 客服相关
        if (intent.contains("投诉") || intent.contains("减免") || intent.contains("罚息") ||
                intent.contains("申诉") || intent.contains("查询状态")) {
            return CUSTOMER_SERVICE;
        }

        // 默认返回协调器
        return COORDINATOR;
    }
}
