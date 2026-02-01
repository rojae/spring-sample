# MCP Transport 방식 비교

MCP(Model Context Protocol) 서버는 세 가지 Transport 방식을 지원합니다.

---

## 클라이언트와 서버란?

```
┌─────────────────────────────────┐      ┌─────────────────────────────────┐
│      클라이언트 (MCP Client)      │      │        서버 (MCP Server)         │
│                                 │      │                                 │
│  - Claude Desktop               │      │  - Spring MCP Server            │
│  - Claude Code                  │ ───▶ │  - (우리가 만든 todo-server)      │
│  - MCP Inspector                │      │                                 │
│                                 │      │                                 │
│  역할:                           │      │  역할:                           │
│  - 사용자와 대화                  │      │  - Tool 실행                     │
│  - 대화 이력 관리                 │      │  - 결과 반환                     │
│  - Tool 호출 결정                │      │  - 비즈니스 로직                  │
└─────────────────────────────────┘      └─────────────────────────────────┘
```

| 구분 | 클라이언트 | 서버 |
|------|-----------|------|
| 예시 | Claude Desktop, Claude Code | Spring MCP Server (todo-server) |
| 하는 일 | "할 일 추가해줘" → Tool 호출 결정 | add_todo 실행 → 결과 반환 |
| 대화 이력 | **저장함** | 저장 안 함 |

---

## 한눈에 보기

| 구분 | STDIO | SSE | Streamable HTTP |
|------|-------|-----|-----------------|
| **프로파일** | `stdio` | `sse` | `streamable` |
| **통신 방식** | stdin/stdout | HTTP + Server-Sent Events | HTTP POST |
| **연결 특성** | 프로세스 간 통신 | 장기 연결 유지 | 요청마다 독립 |
| **용도** | 로컬 연동 | 원격 서버 | 클라우드/K8s |
| **스케일링** | 불가 (로컬) | Sticky Session 필요 | 자유로움 |
| **MCP 스펙** | 2024-11-05 | 2024-11-05 (deprecated) | 2025-03-26 |

---

## 1. STDIO 모드

클라이언트가 MCP 서버 프로세스를 직접 실행하고, 표준 입출력으로 통신합니다.

### 동작 방식

```
┌─────────────────────┐
│   Claude Desktop    │
│   / Claude Code     │
│                     │
│  java -jar xxx.jar  │ ← 프로세스 직접 실행
│         │           │
│      stdin/stdout   │ ← 표준 입출력 통신
│         │           │
│  ┌──────▼────────┐  │
│  │  MCP Server   │  │
│  │  (subprocess) │  │
│  └───────────────┘  │
└─────────────────────┘
```

### 특징

- 네트워크 불필요 (로컬 프로세스)
- 클라이언트당 서버 1개 실행
- 가장 단순하고 안전

### 실행 방법

```bash
java -jar spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```

### Claude Code 연동

```bash
claude mcp add todo-server -- java -jar /path/to/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```

### 설정

```yaml
# application-stdio.yml
spring:
  main:
    web-application-type: none  # 웹서버 없음
  ai:
    mcp:
      server:
        stdio: true

logging:
  level:
    root: OFF  # stdout 오염 방지
```

---

## 2. SSE (Server-Sent Events) 모드

HTTP 기반으로 원격 서버와 통신합니다. 서버→클라이언트 이벤트 푸시에 SSE를 사용합니다.

### 동작 방식

```
┌──────────────────┐         ┌──────────────────┐
│  Claude Desktop  │         │    MCP Server    │
│  / Claude Code   │         │   (HTTP 서버)     │
│                  │         │                  │
│                  │◀═══════▶│ GET /sse         │ ← SSE 스트림 (장기 연결)
│                  │────────▶│ POST /mcp/message│ ← 요청 전송
│                  │         │                  │
└──────────────────┘         └──────────────────┘
```

### 특징

- 2개 채널: SSE 스트림 + POST 엔드포인트
- 연결을 계속 유지해야 함
- 서버가 죽으면 재연결 필요

### 왜 같은 Pod로 가야 하나?

SSE는 2채널 구조이기 때문입니다:

```
클라이언트                                  서버
    │                                        │
    │══════ GET /sse ═══════════════════════▶│ Pod A  ← SSE 스트림 연결
    │◀══════════════════════════════════════ │        (이 연결이 메모리에 유지됨)
    │                                        │
    │────── POST /mcp/message ──────────────▶│ Pod B? ← 다른 Pod로 가면?
    │                                        │
    │  Pod B는 이 클라이언트의 SSE 연결을 모름!
    │  응답을 어디로 보내야 할지 모름
```

**Pod A 메모리:**
```java
Map<String, SseEmitter> sessions = {
    "client-123": SseEmitter  // 이 클라이언트의 SSE 연결
};

// POST 요청 처리 후 SSE로 응답 푸시
sessions.get("client-123").send(response);
```

**Pod B 메모리:**
```java
Map<String, SseEmitter> sessions = {};  // 비어있음!

sessions.get("client-123");  // null - 연결 정보 없음!
// → 응답을 보낼 수 없음
```

반면 **Streamable HTTP**는 응답이 같은 HTTP 연결로 바로 돌아오므로 세션이 필요 없습니다.

### 실행 방법

```bash
java -jar spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse
```

### Claude Code 연동

```bash
claude mcp add todo-server --transport sse http://your-server:8080/sse
```

### 멀티 Pod 배포 시

```yaml
# Kubernetes Ingress - Sticky Session 필요
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/affinity: "cookie"
    nginx.ingress.kubernetes.io/session-cookie-name: "MCP_SESSION"
```

SSE는 장기 연결이므로 클라이언트가 항상 같은 Pod에 연결되어야 합니다.

```
클라이언트 ════════════════════════ Pod A (연결 유지)
                                      ↓ Pod A 죽음
                                    재연결 필요 → Pod B
```

### 설정

```yaml
# application-sse.yml
spring:
  main:
    web-application-type: reactive
  ai:
    mcp:
      server:
        stdio: false
        sse-message-endpoint: /mcp/message
```

---

## 3. Streamable HTTP 모드

최신 MCP 스펙(2025-03-26)에서 SSE를 대체하는 방식입니다. 단일 HTTP 엔드포인트로 통신합니다.

### 동작 방식

```
┌──────────────────┐         ┌──────────────────┐
│  Claude Desktop  │         │    MCP Server    │
│  / Claude Code   │         │   (HTTP 서버)     │
│                  │         │                  │
│                  │────────▶│ POST /mcp        │
│                  │◀────────│ (응답)            │
│                  │         │                  │
│                  │────────▶│ POST /mcp        │
│                  │◀────────│ (응답)            │
└──────────────────┘         └──────────────────┘

각 요청이 독립적 - 연결 유지 불필요
```

### 특징

- 단일 엔드포인트 (`/mcp`)
- 요청마다 독립적 (Stateless 가능)
- 일반 HTTP라서 인프라 친화적

### 실행 방법

```bash
java -jar spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=streamable
```

### Claude Code 연동

```bash
claude mcp add todo-server --transport http http://your-server:8080/mcp
```

### 멀티 Pod 배포 시

```yaml
# Kubernetes - 특별한 설정 없이 스케일 가능
apiVersion: apps/v1
kind: Deployment
spec:
  replicas: 3  # 원하는 만큼
```

Sticky Session이 필요 없습니다:

```
요청 1 ──→ Pod A ──→ 응답
요청 2 ──→ Pod B ──→ 응답
요청 3 ──→ Pod C ──→ 응답
(아무 Pod나 처리 가능)
```

### 설정

```yaml
# application-streamable.yml
spring:
  main:
    web-application-type: servlet
  ai:
    mcp:
      server:
        stdio: false
        protocol: STREAMABLE
```

---

## 대화 이력(Context)은 어디에?

**MCP 서버는 대화 이력을 저장하지 않습니다.**

```
┌──────────────────────────────────────────────────────────────┐
│                     Claude (클라이언트)                        │
│                                                              │
│  대화 이력 전체를 클라이언트가 보관:                            │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ User: "우유 사기 추가해줘"                                │  │
│  │ Assistant: add_todo 호출 → "추가했습니다"                 │  │
│  │ User: "목록 보여줘"                                      │  │
│  │ Assistant: list_todos 호출 → "1. 우유 사기"              │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                            │
                            │ 단순 함수 호출 (Stateless)
                            │ { "name": "add_todo", "arguments": {"title": "우유 사기"} }
                            ▼
                   ┌─────────────────┐
                   │  Load Balancer  │
                   └─────────────────┘
                      │          │
                      ▼          ▼
                   Pod A       Pod B    ← 아무 Pod나 처리
                      │          │
                      └────┬─────┘
                           ▼
                      PostgreSQL       ← 비즈니스 데이터만 저장
```

| 항목 | 저장 위치 | 설명 |
|------|-----------|------|
| 대화 이력 | **클라이언트** | Claude가 관리 |
| Tool 정의 | 서버 (메모리) | 어느 Pod든 동일 |
| 비즈니스 데이터 | DB | Todo 목록 등 |

MCP 서버는 REST API처럼 **Stateless 함수 서버**입니다.
Pod가 여러 개여도 대화 이력에는 영향이 없습니다.

---

## 언제 어떤 모드를 선택할까?

```
┌─────────────────────────────────────────────────────────────────┐
│                        Transport 선택 가이드                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  로컬에서 개인용으로 사용?                                        │
│         │                                                       │
│         ├─ Yes ──→ STDIO (가장 단순)                             │
│         │                                                       │
│         └─ No ──→ 서버로 배포?                                   │
│                        │                                        │
│                        ├─ 단일 서버 ──→ SSE (기존 방식)           │
│                        │                                        │
│                        └─ K8s/클라우드 ──→ Streamable HTTP       │
│                           (멀티 Pod)        (권장)               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

| 상황 | 추천 |
|------|------|
| 로컬 개발/테스트 | STDIO |
| 단일 서버 배포 | SSE 또는 Streamable |
| K8s/클라우드 멀티 Pod | **Streamable HTTP** |
| 레거시 클라이언트 호환 | SSE |

---

## 통신 방식 비교 (WebSocket vs SSE vs Streamable HTTP)

SSE는 WebSocket과 비슷한 특성을 가집니다.

| 구분 | WebSocket | SSE | Streamable HTTP |
|------|-----------|-----|-----------------|
| 연결 | 장기 유지 | 장기 유지 | 요청마다 새로 |
| 방향 | 양방향 | 단방향 (서버→클라) | 요청-응답 |
| 서버 푸시 | O | O | X (폴링 또는 스트림 응답) |
| Sticky Session | 필요 | 필요 | 불필요 |
| 프로토콜 | ws:// | http:// | http:// |

```
WebSocket:     클라 ←─────────────────────→ 서버  (양방향 파이프)

SSE:           클라 ←═════════════════════  서버  (서버→클라 스트림)
               클라  ─── POST ────────────→ 서버  (클라→서버 별도)

Streamable:    클라  ─── POST ────────────→ 서버
               클라 ←─── 응답 ─────────────  서버  (매번 독립)
```

**SSE = "반쪽 WebSocket"** 이라고 보면 됩니다. 서버 푸시는 가능한데 양방향은 아닌.

---

## 참고

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
