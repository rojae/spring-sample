# LLM 설정 가이드 - Ollama + Qwen3-Coder

이 문서는 **spring-mcp-sample** MCP Server와 연동할 로컬 LLM 환경을 구축하는 방법을 다룹니다.

---

## 요약

```bash
# 1. Ollama 설치
brew install ollama

# 2. Ollama 실행
ollama serve

# 3. 모델 다운로드 (19GB)
ollama pull qwen3-coder

# 4. spring-mcp-sample 서버 실행 (SSE 또는 Streamable HTTP)
java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=streamable

# 5. 대화 시작
curl http://localhost:11434/api/chat -d '{
  "model": "qwen3-coder",
  "messages": [{"role": "user", "content": "할 일 목록 보여줘"}],
  "tools": [...]
}'
```

---

## 전체 아키텍처

```
                    "할 일 추가해줘: 보고서 작성"
                              │
                              ▼
┌──────────────────────────────────────────────────────────┐
│                    LLM Client Layer                       │
│                                                          │
│   ┌──────────────┐  ┌──────────┐  ┌──────────────────┐  │
│   │  Claude Code  │  │  Open    │  │  직접 API 호출    │  │
│   │  Claude       │  │  WebUI   │  │  (curl / Python) │  │
│   │  Desktop      │  │          │  │                  │  │
│   └──────┬───────┘  └────┬─────┘  └────────┬─────────┘  │
│          │               │                  │            │
│          │ MCP           │ REST             │ REST       │
│          │ Protocol      │ API              │ API        │
└──────────┼───────────────┼──────────────────┼────────────┘
           │               │                  │
           ▼               ▼                  ▼
┌──────────────────────────────────────────────────────────┐
│               spring-mcp-sample (MCP Server)             │
│                                                          │
│   MCP Tools          REST API                            │
│   ┌────────────┐     ┌─────────────────────┐             │
│   │ add_todo   │     │ GET  /api/todos     │             │
│   │ list_todos │     │ POST /api/todos     │             │
│   │ complete   │     │ PUT  /api/todos/... │             │
│   │ delete     │     │ DEL  /api/todos/... │             │
│   └────────────┘     └─────────────────────┘             │
│                              │                           │
│                    ┌─────────┴─────────┐                 │
│                    │   PostgreSQL 15   │                 │
│                    └──────────────────┘                  │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│                   Ollama (LLM Engine)                     │
│                                                          │
│   ┌──────────────────────────────────────────────────┐   │
│   │  qwen3-coder:30b                                 │   │
│   │  - 30B 파라미터 (MoE, 3.3B 활성)                  │   │
│   │  - 256K 컨텍스트 윈도우                             │   │
│   │  - Function Calling / Tool Use 지원               │   │
│   │  - 에이전틱 코딩에 최적화                            │   │
│   └──────────────────────────────────────────────────┘   │
│                                                          │
│   http://localhost:11434                                  │
└──────────────────────────────────────────────────────────┘
```

**핵심 포인트**: Ollama는 LLM 엔진이고, spring-mcp-sample은 Tool 서버입니다.
LLM이 사용자의 자연어를 이해하고, MCP Tool을 호출할지 판단하며, 결과를 자연어로 변환합니다.

---

## 1. Ollama 설치

### macOS (Homebrew)

```bash
brew install ollama
```

### macOS (직접 설치)

[ollama.com/download](https://ollama.com/download) 에서 `.dmg` 파일 다운로드

### Linux

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows

[ollama.com/download](https://ollama.com/download) 에서 설치 파일 다운로드

### 설치 확인

```bash
ollama --version
# ollama version is 0.x.x
```

---

## 2. Ollama 서버 실행

```bash
ollama serve
```

> macOS에서 Ollama 앱을 설치한 경우 앱 실행만으로 서버가 자동 시작됩니다.

### 실행 확인

```bash
curl http://localhost:11434
# Ollama is running
```

---

## 3. LLM 모델 선택 및 다운로드

### 추천 모델: Qwen3-Coder

| 모델 | 크기 | 파라미터 | 활성 파라미터 | 컨텍스트 | 특징 |
|------|------|---------|-------------|---------|------|
| **qwen3-coder:30b** | 19GB | 30B | 3.3B (MoE) | 256K | Function Calling 최적, 에이전틱 코딩 |
| qwen3-coder:480b | 290GB | 480B | 35B (MoE) | 256K | 최고 성능, 고사양 필요 |

> **MoE (Mixture of Experts)** 아키텍처 덕분에 30B 모델이지만 실제로는 3.3B 파라미터만 활성화됩니다.
> 메모리 효율이 매우 높아 일반 노트북에서도 구동 가능합니다.

```bash
# qwen3-coder 다운로드 (30b, 19GB)
ollama pull qwen3-coder
```

### 대안 모델

리소스가 부족하거나 다른 모델을 선호하는 경우:

| 모델 | 크기 | Function Calling | 비고 |
|------|------|:---:|------|
| `qwen2.5:14b` | 9GB | O | 안정적, 검증된 모델 |
| `qwen2.5:7b` | 4.7GB | O | 가벼움, 저사양 PC |
| `qwen3:8b` | 5GB | O | 빠른 추론 |
| `llama3.1:8b` | 4.7GB | O | Meta 모델, 범용성 |
| `mistral:7b` | 4.1GB | O | 경량, 유럽 모델 |

```bash
# 대안: 가벼운 모델
ollama pull qwen2.5:14b

# 대안: 최소 사양
ollama pull qwen2.5:7b
```

### 다운로드 확인

```bash
ollama list
```

```
NAME                ID              SIZE     MODIFIED
qwen3-coder:30b     abc123def456    19 GB    2 minutes ago
```

---

## 4. 하드웨어 권장 사양

| 모델 | RAM | GPU VRAM | 디스크 | 비고 |
|------|-----|----------|-------|------|
| qwen3-coder:30b | 16GB+ | 12GB+ (권장) | 25GB | MoE로 효율적 |
| qwen2.5:14b | 16GB+ | 10GB+ | 12GB | 안정적 |
| qwen2.5:7b | 8GB+ | 6GB+ | 6GB | 저사양 OK |

> **Apple Silicon (M1/M2/M3/M4)**: 통합 메모리 덕분에 GPU VRAM 걱정 없이 RAM만 충분하면 됩니다.
> M1 Pro 16GB 이상이면 qwen3-coder:30b 구동 가능합니다.

---

## 5. MCP 서버와 연동

### 방법 A: Claude Code / Claude Desktop (MCP 직접 연동)

이 방법에서 Ollama는 필요 없습니다. Claude가 직접 MCP 프로토콜로 Tool을 호출합니다.

자세한 내용은 [README.md](./README.md)의 "Claude Code 연동" / "Claude Desktop 연동" 섹션을 참고하세요.

### 방법 B: Open WebUI + Ollama (REST API 연동)

Ollama가 LLM 역할을 하고, Open WebUI의 Function을 통해 spring-mcp-sample의 REST API를 호출합니다.

자세한 내용은 [INTEGRATION.md](./INTEGRATION.md)를 참고하세요.

### 방법 C: Ollama API 직접 호출 (개발/테스트)

Ollama의 Chat API로 직접 Function Calling을 수행합니다.

#### Step 1: spring-mcp-sample 서버 실행

```bash
# PostgreSQL
docker-compose up -d

# Streamable HTTP 모드 (REST API 포함)
java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=streamable
```

#### Step 2: Ollama에 Tool 정의와 함께 요청

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "qwen3-coder",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant. Use the provided tools to manage todos."
    },
    {
      "role": "user",
      "content": "할 일 목록 보여줘"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "list_todos",
        "description": "List all todo items",
        "parameters": {
          "type": "object",
          "properties": {},
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "add_todo",
        "description": "Add a new todo item",
        "parameters": {
          "type": "object",
          "properties": {
            "title": {
              "type": "string",
              "description": "The title of the todo item"
            }
          },
          "required": ["title"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "complete_todo",
        "description": "Mark a todo as completed",
        "parameters": {
          "type": "object",
          "properties": {
            "id": {
              "type": "integer",
              "description": "The todo ID"
            }
          },
          "required": ["id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "delete_todo",
        "description": "Delete a todo item",
        "parameters": {
          "type": "object",
          "properties": {
            "id": {
              "type": "integer",
              "description": "The todo ID"
            }
          },
          "required": ["id"]
        }
      }
    }
  ],
  "stream": false
}'
```

#### Step 3: Tool Call 응답 처리

Ollama가 Tool Call을 반환하면:

```json
{
  "message": {
    "role": "assistant",
    "content": "",
    "tool_calls": [
      {
        "function": {
          "name": "list_todos",
          "arguments": {}
        }
      }
    ]
  }
}
```

이 응답을 받아서 spring-mcp-sample REST API를 호출합니다:

```bash
# Tool Call 실행
curl http://localhost:8080/api/todos
```

결과를 다시 Ollama에 전달하면 자연어 응답을 생성합니다.

---

## 6. Python으로 전체 흐름 자동화

Tool Calling → REST API 호출 → 응답 생성까지 자동화하는 예제입니다.

```python
#!/usr/bin/env python3
"""
Ollama + spring-mcp-sample 연동 예제
qwen3-coder 모델의 Function Calling으로 Todo를 관리합니다.
"""

import json
import requests

OLLAMA_URL = "http://localhost:11434/api/chat"
MCP_SERVER_URL = "http://localhost:8080"
MODEL = "qwen3-coder"

# MCP Tool 정의
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "list_todos",
            "description": "List all todo items",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_todo",
            "description": "Add a new todo item",
            "parameters": {
                "type": "object",
                "properties": {"title": {"type": "string", "description": "The title of the todo item"}},
                "required": ["title"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "complete_todo",
            "description": "Mark a todo as completed",
            "parameters": {
                "type": "object",
                "properties": {"id": {"type": "integer", "description": "The todo ID"}},
                "required": ["id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_todo",
            "description": "Delete a todo item",
            "parameters": {
                "type": "object",
                "properties": {"id": {"type": "integer", "description": "The todo ID"}},
                "required": ["id"],
            },
        },
    },
]


def execute_tool(name: str, args: dict) -> str:
    """spring-mcp-sample REST API를 호출하여 Tool을 실행합니다."""
    if name == "list_todos":
        resp = requests.get(f"{MCP_SERVER_URL}/api/todos")
        return json.dumps(resp.json(), ensure_ascii=False)
    elif name == "add_todo":
        resp = requests.post(f"{MCP_SERVER_URL}/api/todos", json={"title": args["title"]})
        return json.dumps(resp.json(), ensure_ascii=False)
    elif name == "complete_todo":
        resp = requests.put(f"{MCP_SERVER_URL}/api/todos/{args['id']}/complete")
        return json.dumps(resp.json(), ensure_ascii=False)
    elif name == "delete_todo":
        requests.delete(f"{MCP_SERVER_URL}/api/todos/{args['id']}")
        return f"Todo {args['id']} deleted."
    return "Unknown tool"


def chat(user_message: str) -> str:
    """사용자 메시지를 보내고, Tool Calling이 있으면 자동으로 실행합니다."""
    messages = [
        {"role": "system", "content": "You are a helpful assistant. Use the provided tools to manage todos. Respond in Korean."},
        {"role": "user", "content": user_message},
    ]

    # 1차: LLM에 질문
    resp = requests.post(OLLAMA_URL, json={
        "model": MODEL,
        "messages": messages,
        "tools": TOOLS,
        "stream": False,
    })
    result = resp.json()
    assistant_msg = result["message"]

    # Tool Call이 없으면 바로 응답
    if not assistant_msg.get("tool_calls"):
        return assistant_msg["content"]

    # Tool Call 실행
    messages.append(assistant_msg)
    for tool_call in assistant_msg["tool_calls"]:
        fn = tool_call["function"]
        tool_result = execute_tool(fn["name"], fn.get("arguments", {}))
        messages.append({"role": "tool", "content": tool_result})
        print(f"  [Tool] {fn['name']}({fn.get('arguments', {})}) -> {tool_result}")

    # 2차: Tool 결과를 포함하여 최종 응답 생성
    resp = requests.post(OLLAMA_URL, json={
        "model": MODEL,
        "messages": messages,
        "tools": TOOLS,
        "stream": False,
    })
    return resp.json()["message"]["content"]


if __name__ == "__main__":
    print("=== Ollama + spring-mcp-sample Todo Manager ===\n")

    queries = [
        "할 일에 '보고서 작성' 추가해줘",
        "할 일에 '회의 준비' 추가해줘",
        "현재 할 일 목록 보여줘",
        "1번 할 일 완료 처리해줘",
        "할 일 목록 다시 보여줘",
    ]

    for q in queries:
        print(f"User: {q}")
        answer = chat(q)
        print(f"AI: {answer}\n")
```

### 실행

```bash
pip install requests
python3 ollama_todo.py
```

### 예상 출력

```
=== Ollama + spring-mcp-sample Todo Manager ===

User: 할 일에 '보고서 작성' 추가해줘
  [Tool] add_todo({"title": "보고서 작성"}) -> {"id": 1, "title": "보고서 작성", "completed": false}
AI: '보고서 작성'이 할 일 목록에 추가되었습니다. (ID: 1)

User: 현재 할 일 목록 보여줘
  [Tool] list_todos({}) -> [{"id": 1, "title": "보고서 작성", "completed": false}, ...]
AI: 현재 할 일 목록입니다:
  1. 보고서 작성 (미완료)
  2. 회의 준비 (미완료)
```

---

## 7. 모델 성능 튜닝

### 컨텍스트 윈도우 설정

```bash
# 기본 컨텍스트 (4K) - 빠름
ollama run qwen3-coder

# 큰 컨텍스트 (32K) - 복잡한 대화
ollama run qwen3-coder --ctx-size 32768

# 최대 컨텍스트 (256K) - 메모리 많이 필요
ollama run qwen3-coder --ctx-size 262144
```

### GPU 가속 설정

```bash
# GPU 레이어 수 지정 (Apple Silicon은 자동)
OLLAMA_NUM_GPU=999 ollama serve

# CPU 전용 모드 (GPU 없는 환경)
OLLAMA_NUM_GPU=0 ollama serve
```

### Modelfile로 커스텀 설정

```dockerfile
# Modelfile
FROM qwen3-coder

PARAMETER temperature 0.3
PARAMETER num_ctx 32768
PARAMETER top_p 0.9

SYSTEM """You are a helpful assistant that manages todo items.
Always respond in Korean. Use the provided tools when the user asks about todos."""
```

```bash
# 커스텀 모델 생성
ollama create todo-assistant -f Modelfile

# 실행
ollama run todo-assistant
```

---

## 8. 트러블슈팅

### Ollama가 실행되지 않을 때

```bash
# 포트 충돌 확인
lsof -i :11434

# 다른 Ollama 프로세스 종료
pkill ollama

# 재시작
ollama serve
```

### 모델 다운로드가 느릴 때

```bash
# 다운로드 재시도 (이어받기 지원)
ollama pull qwen3-coder

# 다운로드 상태 확인
ollama list
```

### Function Calling이 작동하지 않을 때

1. 모델이 Function Calling을 지원하는지 확인 (qwen3-coder, qwen2.5 등)
2. Tool 정의 JSON 형식 검증
3. Ollama 버전 업데이트: `brew upgrade ollama`

### 메모리 부족

```bash
# 가벼운 모델로 변경
ollama pull qwen2.5:7b

# 또는 양자화된 버전 사용
ollama pull qwen3-coder:30b-q4_K_M
```

---

## 연동 방식 비교

| 연동 방식 | LLM | 프로토콜 | 장점 | 문서 |
|-----------|-----|---------|------|------|
| **Claude Code/Desktop** | Claude (API) | MCP (STDIO/SSE) | 최고 성능, 네이티브 MCP | [README.md](./README.md) |
| **Open WebUI + Ollama** | Ollama (로컬) | REST API | 무료, 프라이버시 | [INTEGRATION.md](./INTEGRATION.md) |
| **Ollama API 직접** | Ollama (로컬) | REST API | 커스텀 자동화 | 이 문서 |

---

## 참고 링크

- [Ollama 공식 사이트](https://ollama.com)
- [Qwen3-Coder (Ollama)](https://ollama.com/library/qwen3-coder)
- [Ollama API 문서](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Spring AI MCP 문서](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [Model Context Protocol 스펙](https://modelcontextprotocol.io)
