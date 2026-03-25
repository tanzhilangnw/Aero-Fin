# Aero-Fin Quick Start Guide

## 🛠 Prerequisites

| Dependency | Version |
| :--- | :--- |
| **JDK** | 21 |
| **Maven** | 3.8+ |
| **MySQL / OceanBase** | 8.0+ |
| **Redis** | 6.0+ |
| **Milvus** | 2.4.x |

## 🌐 Environment Variables

| Variable | Default | Description |
| :--- | :--- | :--- |
| `DEEPSEEK_API_KEY` | _(required)_ | LLM API key |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | LLM base URL |
| `DEEPSEEK_MODEL` | `deepseek-chat` | Model name |
| `SECURITY_ENABLED` | `true` | Enable API key auth filter |
| `JWT_SECRET` | `change-me-in-production` | JWT signing secret |
| `JWT_EXPIRATION` | `3600000` | JWT TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms) |
| `RATE_LIMIT_ENABLED` | `true` | Enable rate limiting |
| `RATE_LIMIT_RPM` | `60` | Requests per minute |
| `RATE_LIMIT_BURST` | `10` | Burst capacity |

## 🔌 Infrastructure Ports

| Service | Host | Port |
| :--- | :--- | :--- |
| **Application** | localhost | `8080` |
| **MySQL** | localhost | `3306` |
| **Redis** | localhost | `6379` |
| **Milvus** | localhost | `19530` |

## 🚀 Build & Run

```bash
# 1. Build project
mvn clean package -DskipTests

# 2. Run with default profile
mvn spring-boot:run

# 3. Run with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Run the JAR directly (example with env var)
export DEEPSEEK_API_KEY=sk-your-key-here
java -jar target/aero-fin-1.0.0.jar
```

## 🧪 API Test Commands

> **Note**: Default API Key for testing: `sk-*`

### 1. Basic Chat & Session
* **Health Check**
    ```bash
    curl http://localhost:8080/api/chat/health
    ```
* **Create a Session**
    ```bash
    curl -X POST "http://localhost:8080/api/chat/session?userId=user-123"
    ```
* **Non-Streaming Chat**
    ```bash
    curl -X POST http://localhost:8080/api/chat \
      -H "Content-Type: application/json" \
      -H "X-API-Key: sk-*" \
      -d '{"message": "Hello", "sessionId": "session-abc", "userId": "user-123"}'
    ```

### 2. Multi-Agent & Streaming
* **Streaming Chat (SSE)**
    ```bash
    curl -N "http://localhost:8080/api/chat/stream?message=Hello&sessionId=session-abc&userId=user-123" \
      -H "X-API-Key: sk-aerofin-test-2024-xyz789uvw012" \
      -H "Accept: text/event-stream"
    ```
* **Multi-Agent Chat (Reflection Mode)**
    ```bash
    curl -X POST http://localhost:8080/api/chat/multi-agent/reflect \
      -H "Content-Type: application/json" \
      -H "X-API-Key: sk-*" \
      -d '{"message": "Analyze this loan policy", "sessionId": "session-abc", "userId": "user-123"}'
    ```

### 3. RAG / ETL Pipeline
* **Run ETL Pipeline**
    ```bash
    curl -X POST http://localhost:8080/api/etl/run \
      -H "X-API-Key: sk-aerofin-test-2024-xyz789uvw012"
    ```

## 📊 Monitoring & Docs

| Endpoint | Description |
| :--- | :--- |
| `GET /actuator/health` | Health status |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |
| `GET /swagger-ui.html` | OpenAPI / Swagger UI |

## 🧪 Unit Tests
```bash
mvn test
```
*Expected: 16 tests pass (11 in `CoordinatorAgentTest`, 5 in `FinancialToolsTest`).*

---