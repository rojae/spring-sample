# Spring MCP Sample - Todo MCP Server

Spring Boot 기반의 MCP (Model Context Protocol) Server 예제입니다.

## 기술 스택

- Java 21
- Spring Boot 3.2.2
- Spring AI 1.0.3
  - spring-ai-starter-mcp-server (STDIO 모드)
  - spring-ai-starter-mcp-server-webflux (SSE 모드)
- Spring Data JPA
- PostgreSQL 15
- Gradle

## 실행 모드

| 모드 | 프로파일 | 용도 | Transport |
|------|----------|------|-----------|
| **STDIO** | `stdio` | 로컬 Claude Desktop/Code 연동 | stdin/stdout |
| **SSE** | `sse` | HTTP 서버로 외부 사용자 제공 | HTTP + Server-Sent Events |

## 인터페이스

이 서버는 **3가지 방식**으로 접근할 수 있습니다:

| 인터페이스 | 엔드포인트 | 용도 |
|-----------|-----------|------|
| **MCP (STDIO)** | stdin/stdout | Claude Desktop/Code 로컬 연동 |
| **MCP (SSE)** | `GET /sse` | Claude Desktop/Code 원격 연동 |
| **REST API** | `/api/todos/*` | Open WebUI, 사내 시스템 연동 |

## 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                         Clients                                 │
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Claude      │  │ Claude Code │  │ Open WebUI / 사내 시스템 │  │
│  │ Desktop     │  │             │  │                         │  │
│  └──────┬──────┘  └──────┬──────┘  └────────────┬────────────┘  │
│         │                │                      │               │
└─────────┼────────────────┼──────────────────────┼───────────────┘
          │ STDIO/SSE      │ STDIO/SSE            │ REST API
          │ (MCP)          │ (MCP)                │ (HTTP)
          ▼                ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                      │
│                                                                 │
│  ┌───────────────────────┐    ┌───────────────────────────────┐ │
│  │   MCP Server          │    │   REST Controller             │ │
│  │   (STDIO / SSE)       │    │   /api/todos/*                │ │
│  │                       │    │                               │ │
│  │   - add_todo          │    │   GET    /api/todos           │ │
│  │   - list_todos        │    │   POST   /api/todos           │ │
│  │   - complete_todo     │    │   PUT    /api/todos/{id}/...  │ │
│  │   - delete_todo       │    │   DELETE /api/todos/{id}      │ │
│  └───────────┬───────────┘    └───────────────┬───────────────┘ │
│              │                                │                 │
│              └────────────────┬───────────────┘                 │
│                               ▼                                 │
│              ┌───────────────────────────────┐                  │
│              │        TodoRepository         │                  │
│              │       (Spring Data JPA)       │                  │
│              └───────────────┬───────────────┘                  │
│                              │                                  │
└──────────────────────────────┼──────────────────────────────────┘
                               │ JDBC
                               ▼
                 ┌─────────────────────────┐
                 │      PostgreSQL 15      │
                 │    (Docker Container)   │
                 └─────────────────────────┘
```

### 컴포넌트 설명

| 컴포넌트 | 역할 |
|---------|------|
| `McpApplication` | Spring Boot 진입점 |
| `McpServerConfig` | ToolCallbackProvider 빈 등록 |
| `TodoService` | @Tool 어노테이션으로 MCP Tool 정의 |
| `TodoRestController` | REST API 엔드포인트 |
| `TodoRepository` | Spring Data JPA Repository |
| `Todo` | JPA Entity (todos 테이블 매핑) |

## MCP Tool

| Tool 이름 | 설명 | 파라미터 |
|-----------|------|----------|
| `add_todo` | 할 일 추가 | `title` (string, required) |
| `list_todos` | 전체 할 일 목록 조회 | 없음 |
| `complete_todo` | 할 일 완료 처리 | `id` (integer, required) |
| `delete_todo` | 할 일 삭제 | `id` (integer, required) |

## REST API

| Method | Endpoint | 설명 | Request Body |
|--------|----------|------|--------------|
| GET | `/api/todos` | 목록 조회 | - |
| GET | `/api/todos/{id}` | 단건 조회 | - |
| POST | `/api/todos` | 추가 | `{"title": "할일"}` |
| PUT | `/api/todos/{id}/complete` | 완료 처리 | - |
| DELETE | `/api/todos/{id}` | 삭제 | - |

### REST API 사용 예시

```bash
# 목록 조회
curl http://localhost:8080/api/todos

# 추가
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "새 할일"}'

# 완료 처리
curl -X PUT http://localhost:8080/api/todos/1/complete

# 삭제
curl -X DELETE http://localhost:8080/api/todos/1
```

## 실행 방법

### 1. PostgreSQL 실행

```bash
cd spring-mcp-sample
docker-compose up -d
```

### 2. 빌드

```bash
./gradlew :spring-mcp-sample:build
```

### 3. 실행

**STDIO 모드** (로컬 연동용)
```bash
java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```

**SSE 모드** (서버로 제공)
```bash
java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse
```

## Claude Code 연동

### STDIO 모드 (기본)

```bash
claude mcp add todo-server -- java -jar /path/to/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```

### SSE 모드 (원격 서버)

서버를 먼저 실행한 후:
```bash
claude mcp add todo-server --transport sse http://your-server:8080/sse
```

### 연동 확인

```bash
/mcp
```

### 사용 예시

- "할 일에 '보고서 작성하기' 추가해줘"
- "현재 할 일 목록 보여줘"
- "1번 할 일 완료 처리해줘"
- "2번 할 일 삭제해줘"

## Claude Desktop 연동

### 1. 설정 파일 열기

**macOS:**
```bash
code ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

### 2. 설정 추가

**STDIO 모드 (로컬):**
```json
{
  "mcpServers": {
    "todo-server": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/spring-mcp-sample-0.0.1-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ]
    }
  }
}
```

**SSE 모드 (원격 서버 연결):**

> Claude Desktop은 SSE를 직접 지원하지 않으므로 `mcp-remote` 프록시를 사용합니다.

```json
{
  "mcpServers": {
    "todo-server": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://your-server:8080/sse"
      ]
    }
  }
}
```

### 3. Claude Desktop 재시작

## Open WebUI / 사내 시스템 연동

MCP를 지원하지 않는 시스템에서는 **REST API**를 사용합니다.

Open WebUI + Ollama 연동 가이드는 **[INTEGRATION.md](./INTEGRATION.md)** 를 참고하세요.

## 로컬 테스트

### MCP Inspector (GUI)

```bash
npx @modelcontextprotocol/inspector java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```

브라우저에서 `http://localhost:5173` 접속

### SSE 엔드포인트 테스트

```bash
# 서버 실행 후
curl http://localhost:8080/sse -H "Accept: text/event-stream"
```

### REST API 테스트

```bash
curl http://localhost:8080/api/todos
```

### Python 스크립트

```bash
python3 test_mcp.py
```

### DB 직접 확인

```bash
docker exec mcp-postgres psql -U mcp_user -d mcp_db -c "SELECT * FROM todos;"
```

## 설정 파일

### application.yml (공통)
- 애플리케이션 이름, MCP 서버 정보
- PostgreSQL 연결 정보
- JPA 설정

### application-stdio.yml
- `web-application-type: none`
- `stdio: true`
- 로깅 OFF (stdout 오염 방지)

### application-sse.yml
- `web-application-type: reactive`
- `stdio: false`
- 포트 8080
- 로깅 활성화

## 프로젝트 구조

```
spring-mcp-sample/
├── build.gradle
├── docker-compose.yml
├── INTEGRATION.md               # Open WebUI + Ollama 연동 가이드
├── test_mcp.py
├── src/main/java/com/example/mcpsample/
│   ├── McpApplication.java
│   ├── config/
│   │   └── McpServerConfig.java
│   ├── controller/
│   │   └── TodoRestController.java      # REST API
│   ├── domain/
│   │   └── Todo.java
│   ├── repository/
│   │   └── TodoRepository.java
│   └── service/
│       └── TodoService.java             # MCP Tools
└── src/main/resources/
    ├── application.yml                  # 공통 설정
    ├── application-stdio.yml            # STDIO 모드
    └── application-sse.yml              # SSE 모드
```
