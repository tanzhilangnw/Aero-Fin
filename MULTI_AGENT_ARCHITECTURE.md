# 🤖 Aero-Fin 多Agent协作架构

## 架构概览

Aero-Fin 采用**多专家Agent协作架构**，通过Coordinator（中控）+ Expert Agents（专家）的模式，实现复杂金融业务场景的智能处理。

```
┌───────────────────────���─────────────────────────────────────┐
│                      用户请求 (User Request)                  │
└────────────────────────┬────────────────────────────────────┘
                         ↓
┌────────────────────────────────────────────────────────────┐
│             MultiAgentOrchestrator (编排器)                 │
│  - 管理所有Agent生命周期                                      │
│  - 协调Agent间消息传递                                        │
│  - 处理单Agent/多Agent协作                                   │
└────────────────────────┬───────────────────────────────────┘
                         ↓
          ┌──────────────┴──────────────┐
          │   CoordinatorAgent (中控)    │
          │  - 意图识别                  │
          │  - 任务路由                  │
          │  - 结果聚合                  │
          └──────────────┬──────────────┘
                         ↓
   ┌─────────────┬───────┴────────┬────────────────┬──────────────┐
   ↓             ↓                 ↓                ↓              ↓
┌──────┐    ┌──────┐         ┌──────┐        ┌──────┐      ┌──────┐
│ CALC │    │POLICY│         │ RISK │        │ACTION│      │  ... │
│      │    │      │         │      │        │      │      │      │
│贷款  │    │政策  │         │风控  │        │业务  │      │扩展  │
│计算  │    │查询  │         │评估  │        │办理  │      │Agent │
└──────┘    └──────┘         └──────┘        └──────┘      └──────┘
   │             │                │               │             │
   └─────────────┴────────────────┴───────────────┴─────────────┘
                              ↓
                      结果聚合 & 返回用户
```

## 核心组件

### 1. MultiAgentOrchestrator (编排器)

**职责**: 管理所有Agent的生命周期，协调Agent间的消息传递

**核心方法**:
- `processRequest()`: 单Agent处理（非流式）
- `processRequestStream()`: 单Agent处理（流式）
- `processMultiAgentRequest()`: 多Agent协作处理

**文件**: `src/main/java/com/aerofin/agent/MultiAgentOrchestrator.java`

---

### 2. CoordinatorAgent (中控Agent)

**角色**: Supervisor - 智能路由中控

**核心能力**:
1. **强意图识别**: 将复杂自然语言拆解为具体子任务
2. **智能路由**: 根据意图路由到最合适的专家Agent
3. **复合意图处理**: 支持一个请求同时涉及"查+算+办"
4. **优先级判断**: 复合意图时按优先级选择主Agent

**System Prompt**: `AgentSystemPrompts.SUPERVISOR_PROMPT`

**路由规则**:
- 计算优先: 包含具体数字 → CALC
- 业务优先: 包含办理动词 → ACTION
- 政策兜底: 无法确定 → POLICY

**文件**: `src/main/java/com/aerofin/agent/CoordinatorAgent.java`

---

### 3. LoanExpertAgent (贷款计算专家) - CALC

**角色**: 精算师 - 强制工具调用

**核心约束**:
1. **禁止心算**: 必须调用工具，不能大模型自己计算
2. **参数提取**: 从用户输入提取本金、期限、利率
3. **思维链**: 展示 Thought → Action → Observation → Answer 过程

**System Prompt**: `AgentSystemPrompts.CALCULATOR_PROMPT`

**可用工具**:
- `calculateLoan`: 计算月供、总利息、总还款额

**适用场景**:
- "贷款20万，3年期，利率4.5%，每月还多少？"
- "提前还款需要多少钱？"

**文件**: `src/main/java/com/aerofin/agent/experts/LoanExpertAgent.java`

---

### 4. PolicyExpertAgent (政策查询专家) - POLICY

**角色**: 政策专家 - 强制RAG，严禁幻觉

**核心约束**:
1. **严禁臆造**: 所有回答必须基于检索到的context
2. **引用溯源**: 标注信息来源 `[依据：政策文档名称 V版本号]`
3. **兜底机制**: context为空时明确告知"未找到相关政策"
4. **合规拒绝**: 拒绝回答非法话题（套现、洗钱等）

**System Prompt**: `AgentSystemPrompts.POLICY_RAG_PROMPT`

**可用工具**:
- `queryPolicy`: 查询金融政策（向量检索 + 数据库查询）

**RAG工作流程**:
1. 向量相似度搜索
2. 评估检索结果（相似度阈值）
3. 基于context构建回答
4. 引用溯源标注

**适用场景**:
- "小微企业贷款有什么政策？"
- "逾期后有什么后果？"

**文件**: `src/main/java/com/aerofin/agent/experts/PolicyExpertAgent.java`

---

### 5. RiskAssessmentAgent (风控评估专家) - RISK

**角色**: 风控专家 - 多维度风险评估

**核心能力**:
1. **全面评估**: 综合信用历史、财务状况、行为特征、风险信号
2. **风险等级**: GREEN（低风险）/ YELLOW（中风险）/ RED（高风险）
3. **可解释性**: 说明风险判断的依据
4. **合规审慎**: 遵循金融监管要求，保护隐私

**System Prompt**: `AgentSystemPrompts.RISK_ASSESSMENT_PROMPT`

**评估维度**:
- 信用历史: 过往贷款、还款记录
- 财务状况: 收入、负债率
- 行为特征: 登录频率、操作习惯
- 风险信号: 异常行为检测

**适用场景**:
- "我能贷多少？"
- "我的信用情况如何？"

**文件**: `src/main/java/com/aerofin/agent/experts/RiskAssessmentAgent.java`

---

### 6. CustomerServiceAgent (客服专家) - ACTION

**角色**: 业务办理专员 - 遵循SOP标准作业程序

**核心流程** (CRITICAL):
1. **Step 1: 资格校验** - 检查用户是否满足办理条件
2. **Step 2: 风险提示** - 告知操作后果和注意事项
3. **Step 3: 用户确认** - 等待用户明确回复"确认"
4. **Step 4: 执行操作** - 调用业务工具执行写操作
5. **Step 5: 状态反馈** - 告知受理结果和预计时效

**System Prompt**: `AgentSystemPrompts.ACTION_SOP_PROMPT`

**可用工具**:
- `applyWaiver`: 提交罚息减免申请
- `queryWaiverStatus`: 查询申请状态

**适用场景**:
- "我想申请减免500元罚息"
- "查询我的减免申请状态"
- "修改还款日"

**文件**: `src/main/java/com/aerofin/agent/experts/CustomerServiceAgent.java`

---

## Agent间通信

### AgentMessage (消息协议)

**消息类型**:
- `TASK_ASSIGNMENT`: 任务分发（Coordinator → Expert）
- `TASK_RESULT`: 结果返回（Expert → Coordinator）
- `COLLABORATION_REQUEST`: 协作请求（Expert → Expert）
- `INFORMATION_QUERY`: 信息查询
- `CONFIRMATION`: 确认/通知
- `ERROR_REPORT`: 错误报告

**消息结构**:
```java
{
  messageId: "MSG-xxx",
  sender: AgentRole,
  receiver: AgentRole,
  messageType: MessageType,
  content: "消息内容",
  data: Map<String, Object>,  // 结构化数据
  priority: 5,                // 优先级 0-10
  requiresResponse: true,
  parentMessageId: "MSG-yyy",
  sessionId: "SESSION-zzz"
}
```

**文件**: `src/main/java/com/aerofin/agent/AgentMessage.java`

---

## System Prompt 设计原则

### 设计哲学

Aero-Fin 的 Prompt 设计遵循 **强约束 + 明确指令 + 工作流程** 的原则，确保Agent行为可控、可预测、可解释。

### 核心常量类

**文件**: `src/main/java/com/aerofin/config/AgentSystemPrompts.java`

包含以下 System Prompt:
1. `SUPERVISOR_PROMPT` - 中控路由（强意图识别）
2. `POLICY_RAG_PROMPT` - 政策专家（强制RAG，严禁幻觉）
3. `CALCULATOR_PROMPT` - 精算师（强制工具调用，禁止心算）
4. `ACTION_SOP_PROMPT` - 业务专员（SOP标准流程）
5. `RISK_ASSESSMENT_PROMPT` - 风控专家（多维度评估）

### Prompt Engineering 最佳实践

1. **CRITICAL 关键约束**: 用 `##` 标记关键规则，用 `**绝对禁止**` 强调
2. **清单式检查**: 提供 `✅/❌` 检查清单，帮助模型自检
3. **示例驱动**: 提供错误示例 ❌ 和正确示例 ✅
4. **思维链**: 强制输出 Thought → Action → Observation → Answer
5. **兜底机制**: 明确未知情况的处理方式
6. **JSON格式**: 结构化输出，便于解析

---

## 多Agent协作场景

### 场景 1: 单一意图 (Single Agent)

**用户**: "我想贷款20万，3年还清，每月还多少？"

**流程**:
1. CoordinatorAgent 识别意图 → CALC
2. LoanExpertAgent 调用 `calculateLoan` 工具
3. 返回计算结果

---

### 场景 2: 复合意图 (Multi-Agent Collaboration)

**用户**: "我想贷款20万，3年还清，有什么优惠政策吗？"

**流程**:
1. CoordinatorAgent 识别复合意图：计算 + 政策查询
2. 优先路由到 CALC（计算优先）
3. LoanExpertAgent 返回计算结果
4. 标记 `requires_followup: true, followup_agents: ["POLICY"]`
5. MultiAgentOrchestrator 调用 PolicyExpertAgent
6. 聚合两个Agent的结果返回

**实现方法**: `MultiAgentOrchestrator.processMultiAgentRequest()`

---

### 场景 3: Agent间协作

**用户**: "我能贷多少？如果贷50万，每月还多少？"

**流程**:
1. CoordinatorAgent → RISK (风控评估)
2. RiskAssessmentAgent 评估，建议额度 50万
3. RiskAssessmentAgent 发送协作请求 → CALC
4. LoanExpertAgent 计算 50万 的月供
5. 聚合返回完整答案

---

## 性能监控

### Agent指标统计

每个Agent自动记录以下指标：
- `totalProcessed`: 处理总次数
- `totalResponseTime`: 总响应时间
- `totalErrors`: 错误次数
- `avgResponseTime`: 平均响应时间

**查看指标**:
```java
MultiAgentOrchestrator.getAgentStatusSummary()
```

**输出示例**:
```json
{
  "贷款专家": {
    "state": "IDLE",
    "totalProcessed": 150,
    "totalErrors": 2,
    "avgResponseTime": 450
  },
  "政策专家": {
    "state": "IDLE",
    "totalProcessed": 200,
    "totalErrors": 0,
    "avgResponseTime": 320
  }
}
```

---

## 扩展新Agent

### 步骤

1. **定义角色**: 在 `AgentRole` 枚举中添加新角色
2. **创建Agent类**: 继承 `BaseAgent`，实现核心方法
3. **编写Prompt**: 在 `AgentSystemPrompts` 中添加 System Prompt
4. **注册工具**: 实现 `getAvailableTools()` 方法
5. **注册到编排器**: 在 `MultiAgentOrchestrator` 中注册

### 示例: 添加客服质检Agent

```java
@Component
public class QualityAssuranceAgent extends BaseAgent {

    public QualityAssuranceAgent(ChatClient chatClient) {
        super(AgentRole.QUALITY_ASSURANCE, chatClient);
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.QA_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of("auditConversation", "scoreQuality");
    }

    // ... 实现核心方法
}
```

---

## 面试亮点

1. **多Agent协作架构** - Coordinator + Expert 模式
2. **Prompt Engineering** - 强约束、防幻觉、强制工具调用
3. **消息驱动通信** - AgentMessage 协议，支持异步协作
4. **模板方法模式** - BaseAgent 定义标准流程
5. **SOP标准化** - 业务流程标准化（5步法）
6. **RAG强制约束** - 严禁幻觉，引用溯源
7. **性能监控** - 每个Agent自动统计指标
8. **可扩展性** - 插件化设计，轻松添加新Agent

---

## 技术栈

- **Spring Boot 3.4.1** - 核心框架
- **Spring AI 1.0.0-M4** - AI集成
- **Reactor** - 响应式编程（Flux/Mono）
- **Caffeine** - L1缓存
- **Milvus** - 向量数据库（RAG）
- **MySQL** - 关系型数据库

---

## 相关文档

- [QUICK_START.md](QUICK_START.md) - 快速启动指南
- [README.md](README.md) - 项目说明
- [LAYERED_MEMORY_ARCHITECTURE.md](LAYERED_MEMORY_ARCHITECTURE.md) - 分层记忆架构

---

**🎉 Aero-Fin 多Agent协作架构 - 让AI更智能、更可控、更专业！**
