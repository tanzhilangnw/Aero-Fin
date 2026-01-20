# ⚡ Aero-Fin 快速启动指南

## 🎯 5 分钟快速体验

本指南将帮你在 **5 分钟内** 启动并体验 Aero-Fin 的核心功能。

### 📋 前置检查

确保已安装：
- ✅ **Java 21**（`java -version`）
- ✅ **Maven 3.9+**（`mvn -version`）
- ✅ **Docker**（`docker -v`，用于运行 MySQL 和 Milvus）

---

## 第 1 步：启动依赖服务

### 方案 A：使用 Docker（推荐）⭐

```bash
# 1. 启动 MySQL（关系型数据库）
docker run -d \
  --name aero-fin-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=Aero2024 \
  -e MYSQL_DATABASE=aero_fin \
  mysql:8.0

# 2. 启动 Milvus（向量数据库，用于 RAG 检索）
docker run -d \
  --name aero-fin-milvus \
  -p 19530:19530 \
  -p 9091:9091 \
  -e ETCD_USE_EMBED=true \
  -e COMMON_STORAGETYPE=local \
  milvusdb/milvus:v2.4.1-lite

# 3. 启动 Redis（可选，分布式部署时需要）
docker run -d \
  --name aero-fin-redis \
  -p 6379:6379 \
  redis:7

# ✅ 验证服务启动
docker ps
# 应该看到 3 个容器正在运行
```

### 方案 B：本地安装

如果不使用 Docker，请参考：
- **MySQL**: https://dev.mysql.com/downloads/
- **Milvus**: https://milvus.io/docs/install_standalone-docker.md
- **Redis**: https://redis.io/docs/getting-started/

---

## 第 2 步：初始化数据库

```bash
# 进入项目目录
cd Aero-Fin

# 执行数据库初始化脚本
mysql -h localhost -u root -pAero2024 aero_fin < src/main/resources/schema.sql

# ✅ 验证数据是否导入成功
mysql -h localhost -u root -pAero2024 -e "USE aero_fin; SELECT COUNT(*) FROM financial_policies;"
# 应该显示 4 条测试数据
```

---

## 第 3 步：配置 OpenAI API

### 方式 1：环境变量（推荐）⭐

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="sk-your-api-key-here"
$env:OPENAI_BASE_URL="https://api.openai.com"

# Linux/Mac
export OPENAI_API_KEY="sk-your-api-key-here"
export OPENAI_BASE_URL="https://api.openai.com"
```

### 方式 2：修改配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key-here
      base-url: https://api.openai.com
```

**注意**：
- 如果在国内，`base-url` 可以替换为代理地址
- 确保 API Key 有余额

---

## 第 4 步：启动应用

```bash
# 方式 1：直接运行（开发模式，推荐）
mvn spring-boot:run

# 方式 2：打包后运行
mvn clean package -DskipTests
java -jar target/aero-fin-1.0.0.jar
```

**启动成功标志**：

```
----------------------------------------------------------
    Application 'aero-fin' is running! Access URLs:
    Local:      http://localhost:8080
    External:   http://192.168.1.100:8080

    API Endpoints:
    - Chat Stream (SSE):    GET  /api/chat/stream?message=你好
    - Chat (Non-Stream):    POST /api/chat
    - Create Session:       POST /api/chat/session
    - Health Check:         GET  /api/chat/health
----------------------------------------------------------
```

---

## 📐 系统架构说明

Aero-Fin 采用**多Agent协作架构**，通过 Coordinator（中控）+ Expert Agents（专家）的模式处理复杂业务场景。

### 核心Agent角色

1. **CoordinatorAgent (中控)** - 意图识别 + 智能路由
   - 分析用户请求，识别意图类型
   - 路由到最合适的专家Agent
   - 支持复合意图（如：计算+查询）

2. **LoanExpertAgent (贷款专家)** - 强制工具调用
   - 贷款计算（月供、利息、总还款额）
   - 禁止大模型心算，必须调用工具
   - 关键词：贷款、月供、利率、还款

3. **PolicyExpertAgent (政策专家)** - 强制RAG
   - 政策查询（RAG向量检索）
   - 严禁幻觉，所有回答必须基于检索context
   - 关键词：政策、规定、优惠、条件

4. **RiskAssessmentAgent (风控专家)** - 风险评估
   - 用户风险评估、资格审核
   - 多维度分析（信用/财务/行为）
   - 关键词：能否、额度、审批、征信

5. **CustomerServiceAgent (客服专家)** - SOP流程
   - 业务办理（罚息减免、投诉处理）
   - 5步SOP：校验→提示→确认→执行→反馈
   - 关键词：申请、减免、投诉、修改

### 工作流程

```
用户请求 → CoordinatorAgent → 专家Agent → 返回结果
            (意图识别)      (执行任务)
```

详细架构请参考：[MULTI_AGENT_ARCHITECTURE.md](MULTI_AGENT_ARCHITECTURE.md)

---

## 第 5 步：测试核心功能

### 🎨 方式 1：Web 测试页面（推荐新手）

**访问**：
```
http://localhost:8080/index.html
```

**功能体验**：
- ✅ 实时流式输出（打字机效果）
- ✅ 快速提问按钮
- ✅ 精美的聊天界面

---

### 🔧 方式 2：命令行测试（推荐开发者）

#### 测试 1：基础对话（SSE 流式输出）

```bash
curl -N "http://localhost:8080/api/chat/stream?message=你好"
```

**预期输出**：
```
event: message
data: 你好

event: message
data: ！

event: message
data: 我是

event: done
data: [DONE]
```

---

#### 测试 2：贷款计算（工具调用 + 缓存）

**第 1 次请求**（无缓存，耗时 ~500ms）：
```bash
curl -N "http://localhost:8080/api/chat/stream?message=我想贷款20万，3年还清，利率4.5%，每月还多少？"
```

**预期输出**：
```
event: message
data: 根据您的贷款需求计算：

event: message
data: - 贷款本金：20万元
data: - 年利率：4.5%
data: - 贷款期限：36个月

data: 💰 **每月还款额：5,923.45 元**
data: 📊 总还款额：213,244.20 元
data: 📈 总利息：13,244.20 元
```

**第 2 次请求**（缓存命中，耗时 ~2ms）：
```bash
# 再次执行相同请求，观察响应速度
curl -N "http://localhost:8080/api/chat/stream?message=我想贷款20万，3年还清，利率4.5%，每月还多少？"
```

**查看缓存日志**：
```
✅ L1 cache HIT for tool result: calculateLoan
```

---

#### 测试 3：政策查询（RAG 向量检索）

```bash
curl -N "http://localhost:8080/api/chat/stream?message=有哪些小微企业贷款政策？"
```

**预期输出**：
```
找到以下政策：

1. 小微企业经营贷 (POLICY_LOAN_002)
   支持小微企业发展的经营性贷款
   - 贷款额度：10万-300万
   - 年利率：4.35%-6.5%
   - 期限：12-36个月
```

---

#### 测试 4：多轮对话（会话管理 + 槽位填充）

**创建会话**：
```bash
SESSION_ID=$(curl -s -X POST "http://localhost:8080/api/chat/session?userId=user001")
echo "Session ID: $SESSION_ID"
```

**第 1 轮对话**（提供部分信息）：
```bash
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=我想了解贷款"
```

**第 2 轮对话**（补充信息）：
```bash
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=我需要20万"
```

**第 3 轮对话**（完整信息，触发工具调用）：
```bash
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=3年期限，利率4.5%"
```

**预期行为**：
- Agent 会记住之前的对话内容
- 自动填充槽位（principal, termMonths, annualRate）
- 槽位完整后自动调用 `calculateLoan` 工具

---

#### 测试 5：罚息减免申请（写操作 + 数据库）

```bash
curl -N "http://localhost:8080/api/chat/stream?message=我想申请减免500元罚息，账号是LN123456，原因是疫情影响"
```

**预期输出**：
```
罚息减免申请已提交成功！
- 申请编号：WAIVER-20240120153000-ABC12345
- 贷款账号：LN123456
- 减免金额：500.00 元
- 申请状态：待审核
- 提交时间：2024-01-20 15:30:00

请保留申请编号，我们将在 3-5 个工作日内完成审核。
```

**验证数据库**：
```sql
mysql -h localhost -u root -pAero2024 -e "USE aero_fin; SELECT * FROM waiver_applications ORDER BY submitted_at DESC LIMIT 1;"
```

---

#### 测试 6：查询申请状态

```bash
curl -N "http://localhost:8080/api/chat/stream?message=查询申请编号 WAIVER-20240120153000-ABC12345 的状态"
```

---

#### 测试 7：自我修正（检索重试）

```bash
curl -N "http://localhost:8080/api/chat/stream?message=我想了解企业贷款政策"
```

**预期行为**：
```
第 1 次: queryPolicy("keyword", "企业贷款") → 未找到
   ↓
Thought: 检索失败，尝试换关键词
   ↓
第 2 次: queryPolicy("keyword", "小微企业") → 找到政策
   ↓
Answer: [返回政策详情]
```

---

## 🤖 测试多Agent协作功能

### 测试 8：意图识别与路由（单一意图）

**测试贷款计算路由**：
```bash
curl -N "http://localhost:8080/api/chat/stream?message=贷款30万，5年期，利率5%，每月还多少？"
```

**预期行为**：
```
1. CoordinatorAgent 识别意图 → LOAN_EXPERT (贷款计算)
2. LoanExpertAgent 调用 calculateLoan 工具
3. 返回精确计算结果（禁止心算）

输出示例：
Thought: 用户需要计算贷款月供
- 本金：300000元
- 期数：60个月
- 年利率：5%
我将调用 calculateLoan 工具进行精确计算...

💰 月供金额：5,659.98 元
📊 总利息：39,598.80 元
📈 总还款额：339,598.80 元
```

---

**测试政策查询路由**：
```bash
curl -N "http://localhost:8080/api/chat/stream?message=小微企业贷款有什么优惠政策？"
```

**预期行为**：
```
1. CoordinatorAgent 识别意图 → POLICY_EXPERT (政策查询)
2. PolicyExpertAgent 执行RAG检索
3. 基于检索到的context回答（严禁幻觉）

输出特征：
- ✅ 包含政策编号引用
- ✅ 标注来源 [依据：...]
- ✅ 如果检索为空，明确告知"未找到相关政策"
```

---

**测试风控评估路由**：
```bash
curl -N "http://localhost:8080/api/chat/stream?sessionId=SESSION-001&message=我能贷多少钱？"
```

**预期行为**：
```
1. CoordinatorAgent 识别意图 → RISK_ASSESSMENT (风控评估)
2. RiskAssessmentAgent 分析用户画像
3. 返回风险等级和建议额度

输出示例：
🔍 风险评估结果

【风险等级】🟢 低风险 (GREEN)
【综合评分】85 分

【建议额度】最高 80 万元
【建议期限】12-36 个月
【建议利率】4.35%-5.0%

【评估依据】
✅ 信用记录优秀（无逾期）
✅ 收入稳定
⚠️ 征信查询较多
```

---

**测试业务办理路由**：
```bash
curl -N "http://localhost:8080/api/chat/stream?message=我想申请减免300元罚息"
```

**预期行为**：
```
1. CoordinatorAgent 识别意图 → CUSTOMER_SERVICE (业务办理)
2. CustomerServiceAgent 执行SOP流程：
   Step 1: 资格校验
   Step 2: 风险提示
   Step 3: 等待用户确认
   Step 4: 执行操作
   Step 5: 状态反馈

输出特征：
- ⚠️ 必须包含风险提示
- ❓ 要求用户确认
- ✅ 明确告知时效
```

---

### 测试 9：复合意图处理（多Agent协作）

**场景1：计算 + 政策查询**

```bash
curl -N "http://localhost:8080/api/chat/stream?message=我想贷款20万，3年还清，有什么优惠政策吗？"
```

**预期行为**：
```
1. CoordinatorAgent 识别复合意图：
   - 主任务：贷款计算（CALC）
   - 次任务：政策查询（POLICY）

2. 路由策略：计算优先
   - 先调用 LoanExpertAgent 计算月供
   - 标记 requires_followup: true
   - followup_agents: ["POLICY"]

3. MultiAgentOrchestrator 聚合结果：
   - 第一部分：计算结果
   - 第二部分：相关优惠政策

输出示例：
【贷款计算结果】
💰 月供：5,923.45 元
📊 总利息：13,244.20 元

【相关优惠政策】
找到以下政策：
1. 小微企业经营贷 (POLICY_LOAN_002)
   年利率：4.35%-6.5%
   [依据：小微企业信贷政策 V2.1]
```

---

**场景2：风控 + 计算**

```bash
curl -N "http://localhost:8080/api/chat/stream?sessionId=SESSION-002&message=我能贷多少？如果贷50万，每月还多少？"
```

**预期行为**：
```
1. CoordinatorAgent 识别复合意图
2. RiskAssessmentAgent 评估额度
3. 发送协作请求给 LoanExpertAgent
4. 聚合返回完整答案
```

---

### 测试 10：Agent性能监控

**查看Agent状态摘要**：

```bash
# 通过JMX或Actuator查看（需要添加对应endpoint）
curl http://localhost:8080/actuator/metrics/agent.processed.total
```

**预期输出**：
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

## 🧪 测试其他新增功能

### 测试 11：分层记忆（三层记忆架构）

**测试短期记忆（最近 10 条消息）**：
```bash
# 连续发送多条消息
for i in {1..15}; do
  curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=这是第${i}条消息"
  sleep 1
done

# 查询短期记忆（应该只保留最近 10 条）
# 通过日志或监控查看记忆管理
```

**查看日志**：
```
Promoted memory to MID_TERM: memoryId=xxx, importance=0.8
Removed low-importance memory: memoryId=yyy
```

---

### 测试 12：断点续聊（会话暂停与恢复）

**暂停会话**：
```bash
SNAPSHOT_ID=$(curl -s -X POST "http://localhost:8080/api/session/pause?sessionId=$SESSION_ID&userId=user001")
echo "Snapshot ID: $SNAPSHOT_ID"
```

**恢复会话**：
```bash
curl -X POST "http://localhost:8080/api/session/resume?snapshotId=$SNAPSHOT_ID"
```

**预期输出**：
```json
{
  "success": true,
  "sessionId": "SESSION-xxx",
  "summary": "欢迎回来！\n\n上次对话时间：2024-01-20 15:30\n\n历史会话摘要：\n- 会话开始时间：2024-01-20 15:00\n- 消息总数：15 条\n..."
}
```

---

### 测试 13：MCP 工具注册与查询

**查询所有工具**：
```bash
curl "http://localhost:8080/api/tools/list"
```

**预期输出**：
```json
[
  {
    "name": "calculateLoan",
    "category": "financial",
    "description": "计算贷款月供、总利息等信息",
    "cacheable": true,
    "async": false
  },
  {
    "name": "queryPolicy",
    "category": "financial",
    "description": "查询金融政策"
  }
]
```

---

## 📊 监控与日志

### 查看健康检查

```bash
# 应用健康检查
curl http://localhost:8080/api/chat/health
# 输出: OK

# Actuator 健康检查
curl http://localhost:8080/actuator/health
# 输出: {"status":"UP"}
```

### 查看 Prometheus 指标

```bash
curl http://localhost:8080/actuator/prometheus | grep aerofin
```

**关键指标**：
```prometheus
# 工具调用总次数
aerofin_tool_invocations_total{tool="calculateLoan",status="SUCCESS"} 10

# 工具调用耗时（P95）
aerofin_tool_execution_time_seconds{tool="calculateLoan",quantile="0.95"} 0.502

# 缓存命中次数
aerofin_tool_cache_hits_total{tool="calculateLoan"} 8

# 缓存命中率 = 8/10 = 80%
```

### 查看数据库日志

```sql
-- 1. 查看工具调用日志
mysql -h localhost -u root -pAero2024 -e "
USE aero_fin;
SELECT tool_name,
       AVG(execution_time_ms) AS avg_time,
       COUNT(*) AS call_count,
       SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END) AS cache_hits
FROM tool_invocation_logs
GROUP BY tool_name;
"

-- 2. 查看会话历史
mysql -h localhost -u root -pAero2024 -e "
USE aero_fin;
SELECT * FROM conversations
WHERE session_id = 'SESSION-xxx'
ORDER BY created_at DESC
LIMIT 10;
"

-- 3. 统计缓存命中率
mysql -h localhost -u root -pAero2024 -e "
USE aero_fin;
SELECT tool_name,
       ROUND(SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS hit_rate
FROM tool_invocation_logs
GROUP BY tool_name;
"
```

---

## 🔧 常见问题排查

### Q1: 启动报错 "Cannot connect to MySQL"

**检查**：
```bash
# 1. 确认 MySQL 容器正在运行
docker ps | grep mysql

# 2. 测试连接
mysql -h localhost -u root -pAero2024 -e "SELECT 1;"

# 3. 检查配置文件中的密码是否正确
grep "password" src/main/resources/application.yml
```

**解决方案**：
- 确保 MySQL 密码为 `Aero2024`
- 或修改 `application.yml` 中的密码

---

### Q2: 启动报错 "Cannot connect to Milvus"

**检查**：
```bash
# 1. 确认 Milvus 容器正在运行
docker ps | grep milvus

# 2. 查看 Milvus 日志
docker logs aero-fin-milvus
```

**临时禁用 Milvus**（测试其他功能）：
```yaml
# application.yml
spring:
  ai:
    vectorstore:
      milvus:
        enabled: false
```

---

### Q3: OpenAI API 调用失败

**检查清单**：
- ✅ API Key 是否正确（`echo $OPENAI_API_KEY`）
- ✅ 网络是否可访问 OpenAI（`curl https://api.openai.com/v1/models`）
- ✅ API Key 是否有余额
- ✅ 是否配置了代理（如果在国内）

**临时禁用 AI 功能**（测试数据库和缓存）：
```yaml
# application.yml
spring:
  ai:
    openai:
      enabled: false
```

---

### Q4: 前端测试页面打不开

**检查**：
```bash
# 1. 确认应用已启动
curl http://localhost:8080/api/chat/health

# 2. 访问静态资源
curl http://localhost:8080/index.html
```

**如果报 404**：
- 确认 `src/main/resources/static/index.html` 文件存在
- 检查 Spring Boot 静态资源配置

---

## 🚀 进阶测试

### 性能压测

```bash
# 使用 Apache Bench 压测
ab -n 1000 -c 10 "http://localhost:8080/api/chat/stream?message=test"

# 查看缓存命中率提升
```

### 分布式部署测试

```bash
# 1. 启动第 1 个实例（端口 8080）
java -jar target/aero-fin-1.0.0.jar --server.port=8080

# 2. 启动第 2 个实例（端口 8081）
java -jar target/aero-fin-1.0.0.jar --server.port=8081

# 3. 测试会话共享（需要 Redis）
# 在实例 1 创建会话
SESSION_ID=$(curl -s -X POST "http://localhost:8080/api/chat/session")

# 在实例 2 使用相同会话
curl -N "http://localhost:8081/api/chat/stream?sessionId=$SESSION_ID&message=测试跨实例会话"
```

---

## 📊 完整测试检查清单

### ✅ 基础功能
- [ ] 应用启动成功
- [ ] 数据库连接正常
- [ ] 健康检查通过

### ✅ 核心功能
- [ ] 流式对话（SSE）
- [ ] 贷款计算（工具调用）
- [ ] 政策查询（RAG 检索）
- [ ] 多轮对话（会话管理）
- [ ] 罚息减免申请（数据库写入）

### ✅ 多Agent协作功能
- [ ] 意图识别与路由（单一意图）
- [ ] CoordinatorAgent 智能路由
- [ ] LoanExpertAgent 强制工具调用
- [ ] PolicyExpertAgent RAG检索（严禁幻觉）
- [ ] RiskAssessmentAgent 风险评估
- [ ] CustomerServiceAgent SOP流程
- [ ] 复合意图处理（多Agent协作）
- [ ] Agent性能监控

### ✅ 其他新增功能
- [ ] 缓存优化（命中率 > 80%）
- [ ] 分层记忆（记忆提升）
- [ ] 断点续聊（会话恢复）
- [ ] MCP 工具注册

### ✅ 监控与日志
- [ ] Prometheus 指标上报
- [ ] 工具调用日志记录
- [ ] 缓存统计数据

---

## 📚 下一步学习

完成快速启动后，建议阅读：

1. **[MULTI_AGENT_ARCHITECTURE.md](MULTI_AGENT_ARCHITECTURE.md)** - 多Agent协作架构（⭐ 重要）
2. **[LAYERED_MEMORY_ARCHITECTURE.md](LAYERED_MEMORY_ARCHITECTURE.md)** - 分层记忆详解
3. **[README.md](README.md)** - 项目说明
4. **[AgentSystemPrompts.java](src/main/java/com/aerofin/config/AgentSystemPrompts.java)** - Prompt Engineering 设计

---

## 🎯 性能基准

**在单核 2GHz CPU + 4GB RAM 环境下**：

| 指标 | 数值 |
|------|------|
| 应用启动时间 | < 30 秒 |
| 首次请求响应 | 500-1000ms |
| 缓存命中响应 | 2-10ms |
| QPS（单机） | 100-200 |
| 缓存命中率 | 85%+ |

---

## 💬 遇到问题？

- 📧 提交 Issue: https://github.com/yourusername/aero-fin/issues
- 📖 查看 FAQ: [README.md](README.md)
- 🔍 搜索错误日志

---

🎉 **恭喜！你已成功启动 Aero-Fin 系统！**

开始探索更多功能吧！🚀
