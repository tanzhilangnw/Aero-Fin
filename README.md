# 🚀 Aero-Fin - 金融信贷智能客服系统

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0M4-blue.svg)](https://docs.spring.io/spring-ai/reference/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 📋 项目简介

Aero-Fin 是一个**金融信贷智能客服系统**，基于 Spring AI 实现，支持流式输出（SSE）、多 Agent 协作、RAG 向量检索、工具调用（含 MCP 标准化工具）、以及二阶段反思（ReflectAgent）等能力。

### 🎯 核心特性

| 特性 | 说明 | 技术亮点 |
|------|------|----------|
| **流式输出** | SSE 实时打字机效果 | Spring WebFlux + Server-Sent Events |
| **ReAct 模式** | 思考-行动-观察闭环 | Prompt Engineering + Function Calling |
| **工具调用** | 自动执行工具函数 | Spring AI Function Calling + MCP 工具适配 |
| **RAG 检索** | 向量语义检索增强 | Milvus 向量数据库 + Embedding |
| **会话管理** | 滑动窗口上下文控制 | Token 数量优化 + 缓存策略 |
| **自我修正** | 检索失败自动重试 | 智能关键词替换 |
| **多级缓存** | Caffeine + 布隆过滤器 | 缓存穿透保护 + 性能优化 |
| **监控可观测** | AOP + Prometheus | 工具调用全链路监控 |
| **二阶段反思** | 对初稿答案做合规/风险审阅 | ReflectAgent + Reflector Prompt |

---

## 🏗️ 系统架构（概览）

```
┌─────────────────────────────────────────────────────────────┐
│                     前端 (浏览器)                             │
│                   EventSource (SSE)                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│               Controller 层 (ChatController)                 │
│              GET /api/chat/stream (SSE)                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│            Service 层 (AeroFinAgentService)                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 1. 加载会话历史 (ConversationService)                │   │
│  │ 2. 向量检索相关政策 (VectorSearchService → Milvus)   │   │
│  │ 3. 构建 ReAct Prompt (System + RAG + History)        │   │
│  │ 4. 流式调用 ChatClient (Spring AI)                   │   │
│  │ 5. 保存会话记录                                       │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌─────────────────────┐
│  Tools 层                 │    │  Repository 层       │
│  FinancialTools (适配层)  │    │  PolicyRepository   │
│  - calculateLoan → MCP    │    │  ConversationRepo   │
│  - queryPolicy/apply...   │    │  WaiverAppRepo      │
│  (Caffeine缓存)           │    └─────────┬───────────┘
└────────┬────────┘              │
         │                       ▼
         │              ┌─────────────────┐
         │              │  OceanBase/     │
         │              │  MySQL          │
         │              └─────────────────┘
         │
         ▼
┌─────────────────────┐
│  AOP 监控切面        │
│  ToolInvocationAspect│
│  - 记录执行耗时      │
│  - 保存日志到数据库  │
│  - 上报 Prometheus   │
└─────────────────────┘
```

---

## 📦 技术栈

### 核心框架
- **Java 21** - 最新 LTS 版本，支持虚拟线程
- **Spring Boot 3.4** - 企业级应用框架
- **Spring AI 1.0.0-M4** - OpenAI 集成，Function Calling
- **Spring WebFlux** - 响应式编程，支持 SSE

### 数据存储
- **OceanBase / MySQL** - 关系型数据库，存储政策、会话
- **Milvus 2.4** - 向量数据库，语义检索
- **Caffeine** - 高性能本地缓存（Window TinyLFU）

### 监控与工具
- **Micrometer + Prometheus** - 监控指标
- **Spring AOP** - 工具调用监控
- **Guava** - 布隆过滤器，防止缓存穿透
- **Lombok** - 简化代码

---

## 🚀 快速开始

### 1. 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.9+ |
| MySQL/OceanBase | 8.0+ |
| Milvus | 2.4+ |
| Docker (可选) | 20.10+ |

### 2. 启动依赖服务

#### 2.1 启动 MySQL (Docker)
```bash
docker run -d \
  --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=aero_fin \
  mysql:8.0
```

#### 2.2 启动 Milvus (Docker Compose)
```bash
# 下载 Milvus docker-compose.yml
wget https://github.com/milvus-io/milvus/releases/download/v2.4.1/milvus-standalone-docker-compose.yml -O docker-compose.yml

# 启动 Milvus
docker-compose up -d
```

#### 2.3 初始化数据库
```bash
# 执行 schema.sql 创建表结构和测试数据
mysql -h localhost -u root -p aero_fin < src/main/resources/schema.sql
```

### 3. 配置 OpenAI API

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key-here  # 替换为你的 OpenAI API Key
      base-url: https://api.openai.com  # 可替换为代理地址
```

**或使用环境变量**（推荐）:
```bash
export OPENAI_API_KEY=sk-your-api-key-here
export OPENAI_BASE_URL=https://api.openai.com
```

### 4. 启动应用

```bash
# 克隆项目
git clone https://github.com/yourusername/aero-fin.git
cd aero-fin

# 编译打包
mvn clean package -DskipTests

# 运行应用
java -jar target/aero-fin-1.0.0.jar

# 或直接运行
mvn spring-boot:run
```

### 5. 测试接口

#### 5.1 流式对话（SSE）
```bash
# 使用 curl 测试 SSE
curl -N "http://localhost:8080/api/chat/stream?message=我想贷款20万，3年还清，利率4.5%，每月还多少？"

# 响应示例（流式输出）
event: message
data: 根据

event: message
data: 您的

event: message
data: 贷款需求计算：

event: done
data: [DONE]
```

#### 5.2 使用 JavaScript (前端示例)
```html
<!DOCTYPE html>
<html>
<body>
<div id="output"></div>

<script>
const eventSource = new EventSource('http://localhost:8080/api/chat/stream?message=你好');

eventSource.addEventListener('message', function(event) {
  document.getElementById('output').innerHTML += event.data;
});

eventSource.addEventListener('done', function(event) {
  console.log('Stream completed');
  eventSource.close();
});
</script>
</body>
</html>
```

#### 5.3 健康检查
```bash
curl http://localhost:8080/api/chat/health
# 响应: OK

curl http://localhost:8080/actuator/health
# 响应: {"status":"UP"}
```

#### 5.4 多Agent + 反思（非流式）

```bash
curl -X POST "http://localhost:8080/api/chat/multi-agent/reflect" ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"我想贷款20万，3年还清，利率4.5%，每月还多少？\",\"userId\":\"user001\"}"
```

---

## 📊 核心功能演示

### 1. 贷款计算（工具调用）

**用户输入**：
```
我想贷款20万，3年还清，利率4.5%，每月还多少？
```

**Agent 执行流程**：
```
Thought: 用户需要计算等额本息，使用 calculateLoan 工具
Action: calculateLoan(200000, 0.045, 36)
Observation: 月还款额 5923.45 元，总利息 13244.20 元
Answer: [返回格式化结果]
```

**工具调用日志**（数据库 `tool_invocation_logs` 表）：
| tool_name | parameters | execution_time_ms | status | cache_hit |
|-----------|-----------|-------------------|--------|-----------|
| calculateLoan | [200000, 0.045, 36] | 502 | SUCCESS | false |
| calculateLoan | [200000, 0.045, 36] | 2 | SUCCESS | true |

### 2. 政策查询（向量检索 + RAG）

**用户输入**：
```
有没有疫情期间的罚息减免政策？
```

**执行流程**：
1. 向量化查询："疫情罚息减免"
2. 在 Milvus 中检索 Top-5 相似文档
3. 注入 Prompt：`以下是检索到的相关政策信息：[文档1] [文档2]...`
4. LLM 基于检索结果回答

### 3. 自我修正（检索重试）

**用户输入**：
```
我想了解企业贷款政策
```

**执行流程**：
```
第1次: queryPolicy("keyword", "企业贷款") → 未找到
Thought: 检索失败，尝试换关键词
第2次: queryPolicy("keyword", "小微企业") → 找到政策
Answer: [返回政策详情]
```

---

## 🧪 测试用例

### 完整对话示例

```bash
# 1. 创建会话
SESSION_ID=$(curl -X POST "http://localhost:8080/api/chat/session?userId=user001")

# 2. 第一轮对话
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=你好，我想了解贷款"

# 3. 第二轮对话（带上下文）
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=我需要20万，3年期限"

# 4. 第三轮对话（工具调用）
curl -N "http://localhost:8080/api/chat/stream?sessionId=$SESSION_ID&message=帮我计算月供，利率4.5%"
```

---

## 📈 监控指标

### Prometheus 指标

访问 `http://localhost:8080/actuator/prometheus` 查看所有指标：

```prometheus
# 工具调用总次数
aerofin_tool_invocations_total{tool="calculateLoan",status="SUCCESS",cache_hit="false"} 10

# 工具调用耗时（P95）
aerofin_tool_execution_time_seconds{tool="calculateLoan",quantile="0.95"} 0.502

# 缓存命中次数
aerofin_tool_cache_hits_total{tool="calculateLoan"} 8
```

### Grafana 可视化（可选）

1. 导入 Prometheus 数据源
2. 创建 Dashboard，监控：
   - 工具调用 QPS
   - 平均响应时间
   - 缓存命中率
   - 错误率

---

## 🗂️ 项目结构

```
aero-fin/
├── src/main/java/com/aerofin/
│   ├── config/                # 配置类
│   │   ├── AeroFinProperties.java
│   │   ├── CacheConfig.java
│   │   ├── VectorStoreConfig.java
│   │   └── ChatClientConfig.java
│   ├── model/                 # 数据模型
│   │   ├── entity/            # 实体类
│   │   ├── dto/               # 请求/响应 DTO
│   │   └── vo/                # 值对象
│   ├── repository/            # 数据访问层
│   │   ├── PolicyRepository.java
│   │   ├── ConversationRepository.java
│   │   └── WaiverApplicationRepository.java
│   ├── service/               # 业务逻辑层
│   │   ├── AeroFinAgentService.java     # 核心 Agent
│   │   ├── ConversationService.java     # 会话管理
│   │   └── VectorSearchService.java     # 向量检索
│   ├── tools/                 # 工具层
│   │   └── FinancialTools.java          # 金融工具
│   ├── aspect/                # AOP 切面
│   │   └── ToolInvocationAspect.java    # 工具监控
│   ├── controller/            # 控制器层
│   │   ├── ChatController.java
│   │   └── GlobalExceptionHandler.java
│   ├── exception/             # 自定义异常
│   └── AeroFinApplication.java           # 启动类
├── src/main/resources/
│   ├── application.yml        # 配置文件
│   └── schema.sql             # 数据库 Schema
├── pom.xml
└── README.md
```

---

## 💡 面试要点（一句话）

可以重点讲：**Coordinator + Experts 的多 Agent 编排**、**ReAct + 工具调用**、**RAG**、以及新增的 **ReflectAgent 二阶段反思审阅**（合规/风险/逻辑一致性）。

---

## 🔧 常见问题

### Q1: 如何切换向量数据库？
A: 修改 `VectorStoreConfig.java`，Spring AI 支持 Pinecone/Weaviate/Chroma 等多种向量库。

### Q2: 如何添加新工具？
A: 在 `FinancialTools.java` 中添加新方法，并在 `AeroFinAgentService` 的 `.functions()` 中注册。

### Q3: 如何优化缓存命中率？
A: 调整 `application.yml` 中的 `aero-fin.cache.l1.ttl-seconds` 和 `max-size`。

### Q4: 如何部署到生产环境？
A: 当前仓库未提供完整部署文档（TODO）。

---

## 📄 License

MIT License - 详见 [LICENSE](LICENSE) 文件

---

## 👨‍💻 作者

**Aero-Fin Team**

如有问题，欢迎提 Issue 或 PR！

---

⭐ 如果这个项目对你有帮助，请给一个 Star！
