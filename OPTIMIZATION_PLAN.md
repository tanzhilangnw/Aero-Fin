# 🚀 Aero-Fin 项目优化计划

## 📊 优化方向总览

```
优化维度分类：
├─ 🔥 P0 - 必须完成（影响生产/面试）
│  ├─ 1. 补充单元测试和集成测试
│  ├─ 2. 实现API认证授权
│  ├─ 3. 实现API限流防刷
│  └─ 4. 生成API文档（Swagger）
│
├─ 🔥 P1 - 重要改进（激活已有功能）
│  ├─ 5. 暴露断点续聊API
│  ├─ 6. 分层记忆充分利用
│  ├─ 7. 完善异常处理和错误码
│  └─ 8. 配置管理优化
│
├─ 💡 P2 - 性能优化（锦上添花）
│  ├─ 9. 向量检索预加载优化
│  ├─ 10. 批量操作优化
│  ├─ 11. 连接池调优
│  └─ 12. 异步化增强
│
├─ 📈 P3 - 监控增强（可观测性）
│  ├─ 13. 日志规范化
│  ├─ 14. 分布式追踪（Sleuth）
│  ├─ 15. Grafana Dashboard
│  └─ 16. 告警规则配置
│
└─ 📦 P4 - 工程化完善（部署运维）
   ├─ 17. Docker容器化
   ├─ 18. K8s部署配置
   ├─ 19. CI/CD流水线
   └─ 20. 压力测试和性能基准
```

---

## 🔥 P0 - 必须完成（5-8天）

### 1. 补充单元测试和集成测试 ⭐⭐⭐⭐⭐

**当前问题**：0个测试文件，覆盖率0%

**优化目标**：
- 核心类覆盖率 ≥ 70%
- 关键路径覆盖率 ≥ 90%
- 集成测试覆盖主要API

**实施方案**：

#### 1.1 单元测试优先级

```java
// P0 - 必须测试的核心类
1. CoordinatorAgent
   - identifyIntent() - 单Agent意图识别
   - requiresMultiAgent() - 多Agent判断
   - identifyRequiredAgents() - 多Agent识别

2. MultiAgentOrchestrator
   - processRequest() - 单/多Agent编排
   - processMultiAgentInternal() - 多Agent并行执行

3. FinancialTools
   - calculateLoan() - 贷款计算
   - queryPolicy() - 政策查询
   - applyWaiver() - 减免申请

4. VectorSearchService
   - searchRelevantPolicies() - 向量检索
   - formatRetrievedContext() - 上下文格式化

// P1 - 重要类
5. ReflectAgent - 二阶段审阅
6. PolicyExpertAgent - 防幻觉逻辑
7. LoanExpertAgent - 工具调用强制
8. ConversationService - 会话管理
```

#### 1.2 测试框架配置

```xml
<!-- pom.xml 添加依赖 -->
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Reactor 测试 -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-inline</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<!-- 测试覆盖率插件 -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 1.3 测试示例（CoordinatorAgent）

**文件**：`src/test/java/com/aerofin/agent/CoordinatorAgentTest.java`

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.chat.enabled=false"  // 禁用真实API调用
})
class CoordinatorAgentTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @MockBean
    private ChatClient chatClient;

    @Nested
    @DisplayName("单Agent意图识别测试")
    class IdentifyIntentTests {

        @Test
        @DisplayName("识别贷款计算意图")
        void testIdentifyIntent_LoanCalculation() {
            // Given
            String userMessage = "贷款20万，3年，利率4.5%，每月还多少？";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.LOAN_EXPERT);
        }

        @Test
        @DisplayName("识别政策查询意图")
        void testIdentifyIntent_PolicyQuery() {
            String userMessage = "小微企业贷款有什么优惠政策？";
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);
            assertThat(role).isEqualTo(AgentRole.POLICY_EXPERT);
        }

        @Test
        @DisplayName("识别风控评估意图")
        void testIdentifyIntent_RiskAssessment() {
            String userMessage = "我能贷多少额度？";
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);
            assertThat(role).isEqualTo(AgentRole.RISK_ASSESSMENT);
        }

        @Test
        @DisplayName("识别客服办理意图")
        void testIdentifyIntent_CustomerService() {
            String userMessage = "我想申请减免500元罚息";
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);
            assertThat(role).isEqualTo(AgentRole.CUSTOMER_SERVICE);
        }

        @Test
        @DisplayName("默认兜底到贷款专家")
        void testIdentifyIntent_DefaultToLoanExpert() {
            String userMessage = "你好";
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);
            assertThat(role).isEqualTo(AgentRole.LOAN_EXPERT);
        }
    }

    @Nested
    @DisplayName("多Agent协作判断测试")
    class RequiresMultiAgentTests {

        @Test
        @DisplayName("单一领域不触发多Agent")
        void testRequiresMultiAgent_SingleDomain_ReturnsFalse() {
            String userMessage = "贷款20万，每月还多少？";
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("两个领域触发多Agent")
        void testRequiresMultiAgent_TwoDomains_ReturnsTrue() {
            String userMessage = "我想贷款20万，有什么优惠政策吗？";
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("三个领域触发多Agent")
        void testRequiresMultiAgent_ThreeDomains_ReturnsTrue() {
            String userMessage = "我能贷多少？如果贷50万月供多少？有优惠吗？";
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("多Agent识别测试")
    class IdentifyRequiredAgentsTests {

        @Test
        @DisplayName("识别贷款+政策两个Agent")
        void testIdentifyRequiredAgents_LoanAndPolicy() {
            String userMessage = "我想贷款20万，有什么优惠政策吗？";

            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            assertThat(agents)
                .hasSize(2)
                .contains(AgentRole.LOAN_EXPERT, AgentRole.POLICY_EXPERT);
        }

        @Test
        @DisplayName("识别风控+贷款+政策三个Agent")
        void testIdentifyRequiredAgents_RiskLoanPolicy() {
            String userMessage = "我能贷多少额度？如果贷50万，每月还多少？有优惠政策吗？";

            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            assertThat(agents)
                .hasSize(3)
                .contains(
                    AgentRole.RISK_ASSESSMENT,
                    AgentRole.LOAN_EXPERT,
                    AgentRole.POLICY_EXPERT
                );
        }

        @Test
        @DisplayName("单一领域只返回一个Agent")
        void testIdentifyRequiredAgents_SingleAgent() {
            String userMessage = "贷款20万，每月还多少？";

            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            assertThat(agents)
                .hasSize(1)
                .contains(AgentRole.LOAN_EXPERT);
        }
    }
}
```

#### 1.4 集成测试示例（端到端）

**文件**：`src/test/java/com/aerofin/integration/ChatIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ChatIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("流式对话 - 成功返回SSE事件")
    void testChatStream_Success() {
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/chat/stream")
                .queryParam("message", "你好")
                .build())
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
            .expectBodyList(String.class)
            .consumeWith(response -> {
                List<String> events = response.getResponseBody();
                assertThat(events).isNotEmpty();
                assertThat(events.get(events.size() - 1)).contains("[DONE]");
            });
    }

    @Test
    @DisplayName("多Agent协作 - 贷款计算+政策查询")
    void testMultiAgentChat_LoanAndPolicy() {
        ChatRequest request = ChatRequest.builder()
            .message("我想贷款20万买房，3年还清，有什么优惠政策吗？")
            .userId("test-user")
            .build();

        webTestClient.post()
            .uri("/api/chat/multi-agent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(response -> {
                String body = response.getResponseBody();
                assertThat(body).contains("贷款专家", "政策专家");
            });
    }
}
```

**工作量**：3-5天
**收益**：代码质量保证、重构信心、面试加分
**优先级**：🔥🔥🔥🔥🔥 **最高**

---

### 2. 实现API认证授权 ⭐⭐⭐⭐⭐

**当前问题**：任何人都可以调用API，无鉴权

**优化目标**：
- 防止未授权访问
- 支持多种认证方式（API Key / JWT）
- 细粒度权限控制

**实施方案**：

#### 方案A：简单版 - API Key认证（推荐快速实现）

**文件**：`src/main/java/com/aerofin/security/ApiKeyAuthFilter.java`

```java
@Component
@Order(1)
public class ApiKeyAuthFilter implements WebFilter {

    @Value("${aero-fin.security.api-keys}")
    private List<String> validApiKeys;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        // 白名单路径
        if (path.equals("/api/chat/health") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // 提取API Key
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");

        if (apiKey != null && validApiKeys.contains(apiKey)) {
            return chain.filter(exchange);
        } else {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap("{\"error\":\"Unauthorized\",\"message\":\"Invalid API Key\"}".getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
}
```

**配置文件**：`application.yml`

```yaml
aero-fin:
  security:
    api-keys:
      - sk-aerofin-prod-2024-abc123def456
      - sk-aerofin-test-2024-xyz789uvw012
```

**使用示例**：
```bash
curl -H "X-API-Key: sk-aerofin-prod-2024-abc123def456" \
  "http://localhost:8080/api/chat/stream?message=你好"
```

#### 方案B：标准版 - JWT + Spring Security（推荐生产环境）

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
</dependency>
```

**配置类**：`src/main/java/com/aerofin/security/SecurityConfig.java`

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/chat/health", "/actuator/health").permitAll()
                .pathMatchers("/api/auth/login").permitAll()
                .pathMatchers("/api/chat/**").authenticated()
                .anyExchange().authenticated()
            )
            .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
```

**JWT工具类**：`src/main/java/com/aerofin/security/JwtUtil.java`

```java
@Component
public class JwtUtil {

    @Value("${aero-fin.security.jwt.secret}")
    private String secret;

    @Value("${aero-fin.security.jwt.expiration}")
    private long expiration;

    public String generateToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
```

**登录接口**：`src/main/java/com/aerofin/controller/AuthController.java`

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        // 简化版：实际应该验证用户名密码
        if ("admin".equals(request.getUsername()) && "password".equals(request.getPassword())) {
            String token = jwtUtil.generateToken(request.getUsername());
            return Mono.just(LoginResponse.builder()
                .token(token)
                .expiresIn(3600)
                .build());
        } else {
            return Mono.error(new RuntimeException("Invalid credentials"));
        }
    }
}
```

**工作量**：
- 方案A（API Key）：0.5天
- 方案B（JWT）：1-2天

**收益**：生产环境必备、防止滥用
**优先级**：🔥🔥🔥🔥🔥 **最高**

---

### 3. 实现API限流防刷 ⭐⭐⭐⭐

**当前问题**：无限流，可能被刷爆

**优化目标**：
- 每用户每分钟60次请求
- 防止恶意刷量
- 超限返回429状态码

**实施方案**：使用 Bucket4j 令牌桶算法

**依赖**：
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

**限流过滤器**：`src/main/java/com/aerofin/security/RateLimitFilter.java`

```java
@Component
@Order(2)
@Slf4j
public class RateLimitFilter implements WebFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId = getUserId(exchange);

        Bucket bucket = buckets.computeIfAbsent(userId, k -> createBucket());

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded for user: {}", userId);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

            DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}".getBytes());

            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    private Bucket createBucket() {
        // 每分钟60次请求
        Bandwidth limit = Bandwidth.builder()
            .capacity(60)
            .refillIntervally(60, Duration.ofMinutes(1))
            .build();

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private String getUserId(ServerWebExchange exchange) {
        // 从JWT提取userId，或从API Key提取，或使用IP
        String userId = exchange.getAttribute("userId");
        if (userId == null) {
            // 兜底：使用IP地址
            userId = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return userId;
    }
}
```

**限流配置**（可配置化）：`application.yml`

```yaml
aero-fin:
  rate-limit:
    enabled: true
    requests-per-minute: 60
    burst-capacity: 10  # 突发容量
```

**响应头增强**（告知用户剩余配额）：

```java
private Mono<Void> filterWithHeaders(ServerWebExchange exchange, WebFilterChain chain) {
    String userId = getUserId(exchange);
    Bucket bucket = buckets.computeIfAbsent(userId, k -> createBucket());

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
        // 添加响应头
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", "60");
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
            String.valueOf(probe.getRemainingTokens()));
        exchange.getResponse().getHeaders().add("X-RateLimit-Reset",
            String.valueOf(System.currentTimeMillis() / 1000 + 60));

        return chain.filter(exchange);
    } else {
        // 限流
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        // ...
    }
}
```

**测试示例**：
```java
@Test
void testRateLimit_ExceedLimit_Returns429() {
    // 发送61次请求
    for (int i = 0; i < 61; i++) {
        webTestClient.get()
            .uri("/api/chat/stream?message=test")
            .header("X-API-Key", "valid-key")
            .exchange()
            .expectStatus()
            .value(status -> {
                if (i < 60) {
                    assertThat(status).isEqualTo(200);
                } else {
                    assertThat(status).isEqualTo(429);
                }
            });
    }
}
```

**工作量**：1天
**收益**：防止滥用、节省成本
**优先级**：🔥🔥🔥🔥 **高**

---

### 4. 生成API文档（Swagger/OpenAPI） ⭐⭐⭐⭐

**当前问题**：缺少API文档，前后端对接困难

**优化目标**：
- 自动生成API文档
- 支持在线调试
- 导出OpenAPI 3.0规范

**实施方案**：使用 SpringDoc OpenAPI

**依赖**：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**配置**：`application.yml`

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
  info:
    title: Aero-Fin API Documentation
    description: 金融级智能客服系统 API 文档
    version: 1.0.0
    contact:
      name: Aero-Fin Team
      email: support@aerofin.com
```

**Controller注解增强**：

```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "聊天接口 - 支持流式/非流式对话")
public class ChatController {

    @Operation(
        summary = "流式对话接口（SSE）",
        description = "支持Server-Sent Events实时打字机效果，适用于需要逐字输出的场景"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
        ),
        @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "未授权 - API Key无效"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "请求过于频繁 - 超过限流阈值"
        )
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
        @Parameter(description = "用户消息内容", required = true, example = "我想贷款20万，3年还清，每月还多少？")
        @RequestParam String message,

        @Parameter(description = "会话ID（可选，用于多轮对话）", example = "session-abc123")
        @RequestParam(required = false) String sessionId,

        @Parameter(description = "用户ID（可选）", example = "user-123")
        @RequestParam(required = false) String userId
    ) {
        // ...
    }

    @Operation(
        summary = "多Agent协作对话（非流式）",
        description = "自动判断是否需要多Agent协作，并行执行多个专家Agent并聚合结果"
    )
    @PostMapping("/multi-agent")
    public Mono<String> multiAgentChat(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "聊天请求",
            required = true,
            content = @Content(
                schema = @Schema(implementation = ChatRequest.class),
                examples = @ExampleObject(
                    name = "贷款计算+政策查询",
                    value = "{\"message\":\"我想贷款20万买房，3年还清，有什么优惠政策吗？\",\"userId\":\"user-123\"}"
                )
            )
        )
        @Valid @RequestBody ChatRequest request
    ) {
        // ...
    }
}
```

**DTO注解增强**：

```java
@Data
@Builder
@Schema(description = "聊天请求")
public class ChatRequest {

    @Schema(
        description = "用户消息内容",
        example = "我想贷款20万，3年还清，每月还多少？",
        required = true
    )
    @NotBlank(message = "消息内容不能为空")
    private String message;

    @Schema(
        description = "会话ID（用于多轮对话）",
        example = "session-abc123"
    )
    private String sessionId;

    @Schema(
        description = "用户ID",
        example = "user-123"
    )
    private String userId;
}
```

**访问**：
- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/api-docs

**工作量**：0.5-1天
**收益**：便于前后端对接、API文档自动生成
**优先级**：🔥🔥🔥 **中高**

---

## 🔥 P1 - 重要改进（2-3天）

### 5. 暴露断点续聊API ⭐⭐⭐⭐⭐

**当前问题**：ResumeConversationService已实现，但未暴露API

**优化目标**：
- 用户可以暂停/恢复会话
- 查看可恢复的历史会话列表
- 生成恢复摘要

**实施方案**：

**新增Controller方法**：`ChatController.java`

```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ResumeConversationService resumeConversationService;

    @Operation(summary = "暂停会话（保存快照）")
    @PostMapping("/session/{sessionId}/pause")
    public Mono<PauseSessionResponse> pauseSession(
        @Parameter(description = "会话ID") @PathVariable String sessionId,
        @Parameter(description = "用户ID") @RequestParam String userId
    ) {
        return Mono.fromCallable(() -> {
            String snapshotId = resumeConversationService.pauseSession(sessionId, userId);
            return PauseSessionResponse.builder()
                .success(true)
                .snapshotId(snapshotId)
                .message("会话已暂停，快照ID: " + snapshotId)
                .build();
        });
    }

    @Operation(summary = "恢复会话（加载快照）")
    @PostMapping("/session/resume")
    public Mono<ResumeConversationService.ResumeResult> resumeSession(
        @Parameter(description = "快照ID") @RequestParam String snapshotId
    ) {
        return Mono.fromCallable(() ->
            resumeConversationService.resumeSession(snapshotId)
        );
    }

    @Operation(summary = "获取用户的可恢复会话列表")
    @GetMapping("/sessions/recoverable")
    public Mono<List<ResumeConversationService.SessionSummary>> getRecoverableSessions(
        @Parameter(description = "用户ID") @RequestParam String userId
    ) {
        return Mono.fromCallable(() ->
            resumeConversationService.getRecoverableSessions(userId)
        );
    }

    @Operation(summary = "检查会话是否可恢复")
    @GetMapping("/session/{sessionId}/can-resume")
    public Mono<CanResumeResponse> canResumeSession(
        @Parameter(description = "会话ID") @PathVariable String sessionId
    ) {
        return Mono.fromCallable(() -> {
            boolean canResume = resumeConversationService.canResumeSession(sessionId);
            return CanResumeResponse.builder()
                .canResume(canResume)
                .message(canResume ? "会话可恢复" : "会话不存在或已过期")
                .build();
        });
    }
}
```

**DTO定义**：

```java
@Data
@Builder
public class PauseSessionResponse {
    private Boolean success;
    private String snapshotId;
    private String message;
}

@Data
@Builder
public class CanResumeResponse {
    private Boolean canResume;
    private String message;
}
```

**前端集成示例**（JavaScript）：

```javascript
// 1. 用户离开页面时暂停会话
window.addEventListener('beforeunload', async () => {
    await fetch(`/api/chat/session/${sessionId}/pause?userId=${userId}`, {
        method: 'POST',
        headers: { 'X-API-Key': apiKey }
    });
});

// 2. 用户返回时展示可恢复的会话
async function showRecoverableSessions(userId) {
    const response = await fetch(`/api/chat/sessions/recoverable?userId=${userId}`, {
        headers: { 'X-API-Key': apiKey }
    });
    const sessions = await response.json();

    // 渲染会话列表
    sessions.forEach(session => {
        console.log(`会话: ${session.sessionId}, 时间: ${session.lastMessageTime}`);
    });
}

// 3. 用户选择恢复某个会话
async function resumeSession(snapshotId) {
    const response = await fetch(`/api/chat/session/resume?snapshotId=${snapshotId}`, {
        method: 'POST',
        headers: { 'X-API-Key': apiKey }
    });
    const result = await response.json();

    if (result.success) {
        console.log('恢复摘要:', result.summary);
        // 切换到恢复的会话
        currentSessionId = result.sessionId;
    }
}
```

**测试示例**：

```java
@Test
void testPauseAndResumeSession() {
    // 1. 创建会话并对话
    String sessionId = conversationService.createSession("user123");
    conversationService.saveUserMessage(sessionId, "user123", "你好");

    // 2. 暂停会话
    webTestClient.post()
        .uri("/api/chat/session/{sessionId}/pause?userId=user123", sessionId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(PauseSessionResponse.class)
        .value(response -> {
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getSnapshotId()).isNotEmpty();
        });

    // 3. 恢复会话
    webTestClient.post()
        .uri("/api/chat/session/resume?snapshotId=snapshot:{sessionId}", sessionId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ResumeResult.class)
        .value(result -> {
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getSummary()).contains("欢迎回来");
        });
}
```

**工作量**：0.5天
**收益**：激活已实现的核心功能，提升用户体验
**优先级**：🔥🔥🔥🔥🔥 **最高（低成本高收益）**

---

### 6. 分层记忆充分利用 ⭐⭐⭐⭐

**当前问题**：LayeredMemoryManager已实现，但主流程未使用

**优化目标**：
- 长期记忆影响Agent路由决策
- 中期记忆增强上下文理解
- 用户画像个性化推荐

**实施方案**：

#### 6.1 长期记忆影响路由决策

**修改**：`CoordinatorAgent.java`

```java
public AgentRole identifyIntent(String userMessage, String userId) {
    // 1. 获取用户长期偏好
    UserProfile userProfile = memoryManager.getLongTermMemory(userId);

    // 2. 根据用户历史行为调整路由
    if (userProfile != null) {
        // 如果用户频繁进行贷款计算，优先路由到LOAN_EXPERT
        if (userProfile.getFrequentIntent() == AgentRole.LOAN_EXPERT) {
            if (userMessage.contains("贷款") || userMessage.contains("月供")) {
                log.info("[协调器] 根据用户偏好，优先路由到贷款专家");
                return AgentRole.LOAN_EXPERT;
            }
        }

        // 如果用户是企业用户，政策查询优先
        if ("ENTERPRISE".equals(userProfile.getUserType())) {
            if (userMessage.contains("政策") || userMessage.contains("条件")) {
                log.info("[协调器] 企业用户，优先路由到政策专家");
                return AgentRole.POLICY_EXPERT;
            }
        }
    }

    // 3. 原有逻辑
    return identifyAllIntents(userMessage).get(0);
}
```

**UserProfile增强**：

```java
@Data
@Builder
public class UserProfile {
    private String userId;
    private String userType;  // PERSONAL, ENTERPRISE
    private AgentRole frequentIntent;  // 最常用的意图
    private Map<String, Integer> intentCounts;  // 各类意图的使用次数
    private LocalDateTime lastActiveTime;

    // 统计最频繁的意图
    public AgentRole getFrequentIntent() {
        if (intentCounts == null || intentCounts.isEmpty()) {
            return null;
        }
        return intentCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> AgentRole.valueOf(entry.getKey()))
            .orElse(null);
    }
}
```

#### 6.2 中期记忆增强上下文

**修改**：`AeroFinAgentService.java`

```java
public Flux<String> chatStream(String sessionId, String userId, String userMessage) {
    // 1. 获取中期记忆摘要（最近10轮对话主题）
    String midTermSummary = memoryManager.getMidTermMemorySummary(sessionId);

    // 2. 获取短期记忆（最近3轮对话）
    List<Message> conversationHistory = conversationService.getConversationHistory(sessionId);

    // 3. RAG检索
    String ragContext = vectorSearchService.searchRelevantPolicies(userMessage);

    // 4. 构建增强Prompt
    String enhancedPrompt = SYSTEM_PROMPT + "\n\n" +
        "## 最近对话摘要\n" + midTermSummary + "\n\n" +
        "## 检索到的政策信息\n" + ragContext + "\n\n" +
        "请结合最近对话摘要和政策信息，为用户提供连贯的回答。";

    // 5. 流式调用
    return chatClient.prompt()
        .system(enhancedPrompt)
        .messages(conversationHistory)
        .user(userMessage)
        .stream().content();
}
```

**中期记忆摘要生成**：`LayeredMemoryManager.java`

```java
public String getMidTermMemorySummary(String sessionId) {
    // 从Redis获取中期记忆
    String cacheKey = "mid-term:" + sessionId;
    String cached = redisTemplate.opsForValue().get(cacheKey);

    if (cached != null) {
        return cached;
    }

    // 生成摘要（最近10轮对话）
    List<Message> recentMessages = conversationService.getRecentMessages(sessionId, 10);

    if (recentMessages.isEmpty()) {
        return "无历史对话记录";
    }

    // 使用LLM生成摘要
    String summary = chatClient.prompt()
        .user("请总结以下对话的主题和关键信息（1-2句话）：\n" + formatMessages(recentMessages))
        .call()
        .content();

    // 缓存30分钟
    redisTemplate.opsForValue().set(cacheKey, summary, 30, TimeUnit.MINUTES);

    return summary;
}
```

**工作量**：1-2天
**收益**：个性化能力提升、充分利用已有架构
**优先级**：🔥🔥🔥 **中高**

---

### 7. 完善异常处理和错误码 ⭐⭐⭐

**当前问题**：异常处理不统一，缺少错误码体系

**优化目标**：
- 统一错误码规范
- 细化业务异常
- 敏感信息脱敏

**实施方案**：

#### 7.1 错误码枚举

**文件**：`src/main/java/com/aerofin/exception/ErrorCode.java`

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 1xxxx - 通用错误
    SUCCESS(10000, "成功"),
    PARAM_ERROR(10001, "参数错误"),
    SYSTEM_ERROR(10002, "系统异常"),
    UNAUTHORIZED(10003, "未授权"),
    FORBIDDEN(10004, "无权限"),
    RATE_LIMIT_EXCEEDED(10005, "请求过于频繁"),

    // 2xxxx - 业务错误
    SESSION_NOT_FOUND(20001, "会话不存在"),
    SESSION_EXPIRED(20002, "会话已过期"),
    SNAPSHOT_NOT_FOUND(20003, "快照不存在或已过期"),
    CONVERSATION_SAVE_FAILED(20004, "会话保存失败"),

    // 3xxxx - Agent相关
    AGENT_NOT_FOUND(30001, "Agent不存在"),
    AGENT_EXECUTION_FAILED(30002, "Agent执行失败"),
    INTENT_RECOGNITION_FAILED(30003, "意图识别失败"),
    MULTI_AGENT_ORCHESTRATION_FAILED(30004, "多Agent编排失败"),

    // 4xxxx - 工具调用相关
    TOOL_INVOCATION_FAILED(40001, "工具调用失败"),
    TOOL_TIMEOUT(40002, "工具调用超时"),
    TOOL_CACHE_ERROR(40003, "工具缓存异常"),

    // 5xxxx - RAG相关
    VECTOR_SEARCH_FAILED(50001, "向量检索失败"),
    MILVUS_CONNECTION_ERROR(50002, "Milvus连接异常"),
    EMBEDDING_FAILED(50003, "向量化失败"),
    RAG_CONTEXT_EMPTY(50004, "未检索到相关内容"),

    // 6xxxx - LLM相关
    LLM_CALL_FAILED(60001, "LLM调用失败"),
    LLM_TIMEOUT(60002, "LLM调用超时"),
    LLM_RATE_LIMIT(60003, "LLM限流"),
    LLM_QUOTA_EXCEEDED(60004, "LLM配额超限");

    private final Integer code;
    private final String message;
}
```

#### 7.2 统一异常响应

**文件**：`src/main/java/com/aerofin/model/dto/ErrorResponse.java`

```java
@Data
@Builder
@Schema(description = "错误响应")
public class ErrorResponse {

    @Schema(description = "错误码", example = "20001")
    private Integer code;

    @Schema(description = "错误消息", example = "会话不存在")
    private String message;

    @Schema(description = "详细信息（生产环境可隐藏）", example = "Session ID abc123 not found")
    private String detail;

    @Schema(description = "追踪ID", example = "trace-abc123")
    private String traceId;

    @Schema(description = "时间戳", example = "2024-01-20T15:30:00")
    private LocalDateTime timestamp;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String detail) {
        return ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .detail(detail)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

#### 7.3 业务异常类

**文件**：`src/main/java/com/aerofin/exception/BusinessException.java`

```java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
```

#### 7.4 全局异常处理增强

**修改**：`GlobalExceptionHandler.java`

```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        String traceId = MDC.get("traceId");

        log.warn("[{}] 业务异常: code={}, message={}, detail={}",
            traceId, e.getErrorCode().getCode(), e.getMessage(), e.getDetail());

        ErrorResponse response = ErrorResponse.builder()
            .code(e.getErrorCode().getCode())
            .message(e.getErrorCode().getMessage())
            .detail(maskSensitiveInfo(e.getDetail()))  // 脱敏
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        String traceId = MDC.get("traceId");

        log.error("[{}] 系统异常", traceId, e);

        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.SYSTEM_ERROR.getCode())
            .message(ErrorCode.SYSTEM_ERROR.getMessage())
            .detail("系统异常，请联系管理员")  // 生产环境隐藏详情
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }

    /**
     * 敏感信息脱敏
     */
    private String maskSensitiveInfo(String detail) {
        if (detail == null) {
            return null;
        }

        // 脱敏身份证号：保留前3后4
        detail = detail.replaceAll("(\\d{3})\\d{11}(\\d{4})", "$1***********$2");

        // 脱敏手机号：保留前3后4
        detail = detail.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");

        // 脱敏银行卡号：保留后4位
        detail = detail.replaceAll("\\d{12,16}(\\d{4})", "************$1");

        return detail;
    }
}
```

#### 7.5 使用示例

```java
// Service层抛出业务异常
public String pauseSession(String sessionId, String userId) {
    SessionState state = cacheManager.getSessionState(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.SESSION_NOT_FOUND,
            "Session ID: " + sessionId
        ));

    // ...
}

// Controller自动捕获并返回统一格式
// 响应示例：
{
  "code": 20001,
  "message": "会话不存在",
  "detail": "Session ID: abc123",
  "traceId": "trace-xyz789",
  "timestamp": "2024-01-20T15:30:00"
}
```

**工作量**：1天
**收益**：异常处理规范化、便于排查问题
**优先级**：🔥🔥🔥 **中**

---

### 8. 配置管理优化 ⭐⭐⭐

**当前问题**：部分配置硬编码，不易调整

**优化目标**：
- 硬编码 → 配置化
- 支持环境分离（dev/test/prod）
- 敏感信息加密

**实施方案**：

#### 8.1 配置文件分离

**目录结构**：
```
src/main/resources/
├── application.yml               # 公共配置
├── application-dev.yml          # 开发环境
├── application-test.yml         # 测试环境
├── application-prod.yml         # 生产环境
└── bootstrap.yml                # 引导配置（Nacos）
```

**公共配置**：`application.yml`

```yaml
spring:
  application:
    name: aero-fin
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

# 通用配置
aero-fin:
  # Prompt配置
  prompts:
    reload-enabled: true  # 是否支持热更新
    cache-seconds: 3600  # Prompt缓存时间

  # RAG配置
  rag:
    similarity-threshold:
      high: ${RAG_SIMILARITY_HIGH:0.8}
      medium: ${RAG_SIMILARITY_MEDIUM:0.5}
      low: ${RAG_SIMILARITY_LOW:0.3}
    top-k: ${RAG_TOP_K:5}
    retry-times: ${RAG_RETRY_TIMES:2}

  # 多Agent配置
  multi-agent:
    enabled: true
    domain-count-threshold: ${MULTI_AGENT_THRESHOLD:2}
    parallel-execution: true

  # 缓存配置
  cache:
    l1:
      ttl-seconds: ${CACHE_L1_TTL:3600}
      max-size: ${CACHE_L1_MAX_SIZE:10000}
    bloom-filter:
      enabled: true
      expected-insertions: 10000
      fpp: 0.01  # 误判率

  # 限流配置
  rate-limit:
    enabled: ${RATE_LIMIT_ENABLED:true}
    requests-per-minute: ${RATE_LIMIT_RPM:60}
    burst-capacity: ${RATE_LIMIT_BURST:10}

  # 安全配置
  security:
    api-keys: ${AERO_FIN_API_KEYS:sk-dev-key-123}
    jwt:
      secret: ${JWT_SECRET:your-secret-key-change-me-in-production}
      expiration: ${JWT_EXPIRATION:3600000}  # 1小时

  # 会话配置
  session:
    max-history-count: ${SESSION_MAX_HISTORY:20}
    snapshot-ttl-days: ${SESSION_SNAPSHOT_TTL:30}
```

**开发环境**：`application-dev.yml`

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:11434  # 本地Ollama
      api-key: ollama  # 本地不需要真实key

logging:
  level:
    com.aerofin: DEBUG

aero-fin:
  rate-limit:
    enabled: false  # 开发环境关闭限流
```

**生产环境**：`application-prod.yml`

```yaml
spring:
  ai:
    openai:
      base-url: https://api.openai.com
      api-key: ${OPENAI_API_KEY}  # 从环境变量读取

logging:
  level:
    com.aerofin: INFO

aero-fin:
  prompts:
    reload-enabled: false  # 生产环境禁用热更新

  rate-limit:
    enabled: true
    requests-per-minute: 60

  security:
    api-keys: ${AERO_FIN_API_KEYS}  # 必须从环境变量读取
```

#### 8.2 配置类

**文件**：`src/main/java/com/aerofin/config/AeroFinProperties.java`

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "aero-fin")
public class AeroFinProperties {

    private Prompts prompts = new Prompts();
    private Rag rag = new Rag();
    private MultiAgent multiAgent = new MultiAgent();
    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();
    private Security security = new Security();
    private Session session = new Session();

    @Data
    public static class Prompts {
        private Boolean reloadEnabled = true;
        private Integer cacheSeconds = 3600;
    }

    @Data
    public static class Rag {
        private SimilarityThreshold similarityThreshold = new SimilarityThreshold();
        private Integer topK = 5;
        private Integer retryTimes = 2;

        @Data
        public static class SimilarityThreshold {
            private Double high = 0.8;
            private Double medium = 0.5;
            private Double low = 0.3;
        }
    }

    @Data
    public static class MultiAgent {
        private Boolean enabled = true;
        private Integer domainCountThreshold = 2;
        private Boolean parallelExecution = true;
    }

    @Data
    public static class Cache {
        private L1 l1 = new L1();
        private BloomFilter bloomFilter = new BloomFilter();

        @Data
        public static class L1 {
            private Integer ttlSeconds = 3600;
            private Integer maxSize = 10000;
        }

        @Data
        public static class BloomFilter {
            private Boolean enabled = true;
            private Integer expectedInsertions = 10000;
            private Double fpp = 0.01;
        }
    }

    @Data
    public static class RateLimit {
        private Boolean enabled = true;
        private Integer requestsPerMinute = 60;
        private Integer burstCapacity = 10;
    }

    @Data
    public static class Security {
        private List<String> apiKeys = List.of();
        private Jwt jwt = new Jwt();

        @Data
        public static class Jwt {
            private String secret = "change-me";
            private Long expiration = 3600000L;
        }
    }

    @Data
    public static class Session {
        private Integer maxHistoryCount = 20;
        private Integer snapshotTtlDays = 30;
    }
}
```

#### 8.3 使用示例

```java
@Service
@RequiredArgsConstructor
public class CoordinatorAgent {

    private final AeroFinProperties properties;

    public boolean requiresMultiAgent(String userMessage) {
        int domainCount = 0;
        // ...

        // 从配置读取阈值
        int threshold = properties.getMultiAgent().getDomainCountThreshold();
        return domainCount >= threshold;
    }
}
```

#### 8.4 Nacos集成（可选）

**依赖**：
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    <version>2023.0.1.0</version>
</dependency>
```

**bootstrap.yml**：
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        namespace: aero-fin
        group: DEFAULT_GROUP
        file-extension: yml
        refresh-enabled: true  # 支持配置热更新
```

**工作量**：1天
**收益**：灵活调整配置、支持A/B测试
**优先级**：🔥🔥 **中**

---

## 💡 P2 - 性能优化（2-3天）

### 9. 向量检索预加载优化 ⭐⭐⭐

**当前问题**：每次查询都需要向Milvus发起请求

**优化目标**：
- 热门政策预加载到内存
- 冷启动优化
- 减少网络开销

**实施方案**：

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    // 热门政策缓存（启动时预加载）
    private final Map<String, List<Document>> hotPolicyCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void preloadHotPolicies() {
        log.info("预加载热门政策...");

        List<String> hotQueries = List.of(
            "小微企业贷款",
            "首套房贷款",
            "消费贷款",
            "经营贷款",
            "罚息减免"
        );

        hotQueries.forEach(query -> {
            try {
                List<Document> docs = searchRelevantPolicies(query);
                hotPolicyCache.put(query, docs);
                log.info("预加载政策: {} -> {} 条文档", query, docs.size());
            } catch (Exception e) {
                log.error("预加载失败: {}", query, e);
            }
        });

        log.info("预加载完成，缓存 {} 个热门查询", hotPolicyCache.size());
    }

    public List<Document> searchRelevantPolicies(String query) {
        // 1. 检查预加载缓存
        if (hotPolicyCache.containsKey(query)) {
            log.info("命中预加载缓存: {}", query);
            return hotPolicyCache.get(query);
        }

        // 2. 检查Caffeine缓存
        // 3. Milvus查询
        // ...
    }
}
```

**工作量**：0.5天
**收益**：冷启动优化、热门查询0网络开销
**优先级**：💡 **低**

---

### 10-12. 其他性能优化（批量操作、连接池调优、异步化增强）

**详细方案见文档后续部分...**

---

## 📈 P3 - 监控增强（1-2天）

### 13. 日志规范化 ⭐⭐⭐

**实施方案**：

```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.aerofin.controller..*(..))")
    public Object logController(ProceedingJoinPoint pjp) throws Throwable {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }

        String method = pjp.getSignature().toShortString();
        Object[] args = pjp.getArgs();

        log.info("[{}] >>> API调用: method={}, args={}", traceId, method, maskArgs(args));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("[{}] <<< API成功: method={}, duration={}ms", traceId, method, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] <<< API失败: method={}, duration={}ms, error={}",
                traceId, method, duration, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }

    private Object[] maskArgs(Object[] args) {
        // 脱敏逻辑
        return args;
    }
}
```

**工作量**：0.5天
**优先级**：💡 **中低**

---

## 📦 P4 - 工程化完善（2-3天）

### 17. Docker容器化 ⭐⭐⭐⭐

**Dockerfile**：

```dockerfile
FROM openjdk:21-jdk-slim

WORKDIR /app

# 复制jar包
COPY target/aero-fin-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 环境变量
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Docker Compose**：

```yaml
version: '3.8'

services:
  aero-fin:
    build: .
    container_name: aero-fin-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - MYSQL_HOST=mysql
      - REDIS_HOST=redis
      - MILVUS_HOST=milvus
    depends_on:
      - mysql
      - redis
      - milvus
    restart: unless-stopped
    networks:
      - aero-fin-network

  mysql:
    image: mysql:8.0
    container_name: aero-fin-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: aero_fin
    volumes:
      - mysql-data:/var/lib/mysql
      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql
    ports:
      - "3306:3306"
    networks:
      - aero-fin-network

  redis:
    image: redis:7-alpine
    container_name: aero-fin-redis
    ports:
      - "6379:6379"
    networks:
      - aero-fin-network

  milvus:
    image: milvusdb/milvus:v2.4.1
    container_name: aero-fin-milvus
    ports:
      - "19530:19530"
    environment:
      ETCD_ENDPOINTS: etcd:2379
    depends_on:
      - etcd
    networks:
      - aero-fin-network

  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: aero-fin-etcd
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
    networks:
      - aero-fin-network

volumes:
  mysql-data:

networks:
  aero-fin-network:
    driver: bridge
```

**一键启动**：
```bash
docker-compose up -d
```

**工作量**：1天
**收益**：一键部署、环境隔离
**优先级**：🔥🔥🔥 **中高**

---

## 🎯 优先级总结表

| 编号 | 优化项 | 优先级 | 工作量 | 收益 | 建议时间 |
|-----|--------|--------|--------|------|---------|
| 1 | 补充单元测试 | 🔥🔥🔥🔥🔥 | 3-5天 | 代码质量保证 | **立即开始** |
| 2 | API认证授权 | 🔥🔥🔥🔥🔥 | 1-2天 | 生产必备 | **本周** |
| 3 | API限流防刷 | 🔥🔥🔥🔥 | 1天 | 防止滥用 | **本周** |
| 4 | API文档生成 | 🔥🔥🔥 | 0.5-1天 | 便于对接 | **本周** |
| 5 | 断点续聊API | 🔥🔥🔥🔥🔥 | 0.5天 | **低成本高收益** | **本周** |
| 6 | 分层记忆集成 | 🔥🔥🔥 | 1-2天 | 个性化提升 | 下周 |
| 7 | 异常处理优化 | 🔥🔥🔥 | 1天 | 规范化 | 下周 |
| 8 | 配置管理优化 | 🔥🔥 | 1天 | 灵活调整 | 下周 |
| 9-12 | 性能优化 | 💡💡 | 2-3天 | 锦上添花 | 有时间再做 |
| 13-16 | 监控增强 | 💡💡 | 1-2天 | 可观测性 | 有时间再做 |
| 17-20 | 工程化完善 | 🔥🔥🔥 | 2-3天 | 部署便利 | 有时间再做 |

---

## 💬 我的建议

### 本周重点（必须完成）

```
Day 1-2: 补充核心类单元测试（CoordinatorAgent, MultiAgentOrchestrator）
Day 3: 实现API认证（推荐API Key快速方案）
Day 4: 实现API限流 + 暴露断点续聊API（两个0.5天任务）
Day 5: 生成API文档（SpringDoc）
```

### 下周重点（重要改进）

```
Day 1: 分层记忆集成到主流程
Day 2: 完善异常处理和错误码
Day 3: 配置管理优化
Day 4-5: 继续补充测试（目标覆盖率70%）
```

### 后续优化（有时间再做）

```
- 性能优化（向量预加载、批量操作）
- 日志规范化
- Docker容器化
- 压力测试
```

---

## 🤔 请你审阅

**请选择**：

1. ✅ **全部认可**：我按照这个计划开始实施
2. 🔧 **部分修改**：请告诉我哪些需要调整
3. 🎯 **优先级调整**：请告诉我你认为最重要的3-5项
4. ❓ **有疑问**：请指出哪些部分需要我详细解释

**你的意见**？
