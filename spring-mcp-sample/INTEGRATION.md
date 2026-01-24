# Open WebUI + Ollama 연동 가이드

이 문서는 spring-mcp-sample의 REST API를 Open WebUI + Ollama 환경에서 사용하는 방법을 설명합니다.

## 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                         사용자                                   │
│                   "할 일 목록 보여줘"                             │
└───────────────────────────┬─────────────────────────────────────┘
                            │ http://localhost:3030
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Open WebUI                                  │
│                   (Docker, 포트 3030)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ (1) 자연어 전달
┌─────────────────────────────────────────────────────────────────┐
│                        Ollama                                    │
│                   (로컬 LLM 서버)                                 │
│                                                                 │
│   "할 일 목록 보여줘" 분석                                        │
│         ↓                                                       │
│   list_todos() 호출 결정                                         │
│                                                                 │
│   - qwen2.5:14b / llama3.1:8b / mistral 등                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ (2) Function Call 요청
┌─────────────────────────────────────────────────────────────────┐
│                      Open WebUI                                  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Function: Todo Manager 실행                                ││
│  │  list_todos() 호출                                          ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ (3) REST API 호출
┌─────────────────────────────────────────────────────────────────┐
│                   spring-mcp-sample                              │
│                  (REST API 서버)                                 │
│                                                                 │
│   GET /api/todos                                                │
│   → [{"id":1,"title":"보고서","completed":false}, ...]          │
│                                                                 │
│   http://localhost:8080                                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ (4) 결과 반환
┌─────────────────────────────────────────────────────────────────┐
│                        Ollama                                    │
│                                                                 │
│   결과를 자연어로 변환:                                           │
│   "현재 할 일 목록입니다:                                         │
│    1. 보고서 (미완료)                                            │
│    2. 회의 준비 (완료)"                                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ (5) 응답
┌─────────────────────────────────────────────────────────────────┐
│                         사용자                                   │
└─────────────────────────────────────────────────────────────────┘
```

## 1. Ollama 설치

### macOS

```bash
brew install ollama
```

### Linux

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows

[Ollama 공식 사이트](https://ollama.com/download)에서 설치 파일 다운로드

### Ollama 실행

```bash
ollama serve
```

## 2. LLM 모델 다운로드

Function Calling을 잘 지원하는 모델을 추천합니다:

```bash
# QWEN 2.5 (추천)
ollama pull qwen2.5:14b

# 또는 더 가벼운 버전
ollama pull qwen2.5:7b

# 또는 Llama 3.1
ollama pull llama3.1:8b
```

### 모델 확인

```bash
ollama list
```

## 3. Open WebUI 설치 (Docker)

### Docker로 실행 (포트 3030)

```bash
docker run -d \
  --name open-webui \
  -p 3030:8080 \
  -v open-webui:/app/backend/data \
  --add-host=host.docker.internal:host-gateway \
  --restart always \
  ghcr.io/open-webui/open-webui:main
```

### 옵션 설명

| 옵션 | 설명 |
|------|------|
| `-p 3030:8080` | 브라우저에서 3030 포트로 접속 |
| `-v open-webui:/app/backend/data` | 데이터 영속화 |
| `--add-host=host.docker.internal:host-gateway` | 호스트 네트워크 접근 허용 |

### 실행 확인

```bash
docker ps | grep open-webui
```

### 브라우저 접속

```
http://localhost:3030
```

처음 접속 시 관리자 계정을 생성합니다.

## 4. Open WebUI 설정

### Ollama 연결 설정

1. Open WebUI 접속 (`http://localhost:3030`)
2. **Settings** (설정) → **Connections**
3. Ollama URL 확인: `http://host.docker.internal:11434`
4. 연결 테스트 후 저장

## 5. Todo Manager Function 등록

### Function 생성

1. **Workspace** → **Functions** → **+** (새 Function)
2. 아래 코드 붙여넣기
3. 저장 후 활성화

### Function 코드

```python
"""
title: Todo Manager
author: rojae
version: 0.1.0
"""

import urllib.request
import urllib.error
import json
from pydantic import BaseModel, Field
from typing import Optional


class Tools:
    def __init__(self):
        self.base_url = "http://host.docker.internal:8080"

    def _request(self, method, path, data=None):
        url = self.base_url + path
        headers = {"Content-Type": "application/json"}

        if data:
            req = urllib.request.Request(
                url,
                data=json.dumps(data).encode("utf-8"),
                headers=headers,
                method=method
            )
        else:
            req = urllib.request.Request(url, headers=headers, method=method)

        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                if response.status == 204:
                    return {}
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return {"error": "not_found"}
            raise

    def list_todos(self) -> str:
        """List all todo items"""
        try:
            todos = self._request("GET", "/api/todos")
            if not todos:
                return "No todos found."

            lines = ["Todo List:"]
            for todo in todos:
                status = "[Done]" if todo.get("completed") else "[Open]"
                line = "{} [{}] {}".format(status, todo.get("id"), todo.get("title"))
                lines.append(line)
            return chr(10).join(lines)
        except Exception as e:
            return "Error: {}".format(str(e))

    def add_todo(self, title: str) -> str:
        """Add a new todo item. Parameter: title - the todo title"""
        try:
            todo = self._request("POST", "/api/todos", {"title": title})
            return "Added - ID: {}, Title: {}".format(todo.get("id"), todo.get("title"))
        except Exception as e:
            return "Error: {}".format(str(e))

    def complete_todo(self, id: int) -> str:
        """Mark a todo as completed. Parameter: id - the todo ID"""
        try:
            path = "/api/todos/{}/complete".format(id)
            result = self._request("PUT", path)
            if result.get("error") == "not_found":
                return "ID {} not found.".format(id)
            return "Completed: [{}] {}".format(result.get("id"), result.get("title"))
        except Exception as e:
            return "Error: {}".format(str(e))

    def delete_todo(self, id: int) -> str:
        """Delete a todo item. Parameter: id - the todo ID"""
        try:
            path = "/api/todos/{}".format(id)
            result = self._request("DELETE", path)
            if result.get("error") == "not_found":
                return "ID {} not found.".format(id)
            return "ID {} deleted.".format(id)
        except Exception as e:
            return "Error: {}".format(str(e))
```

## 6. spring-mcp-sample 서버 실행

```bash
# PostgreSQL 실행
docker-compose up -d

# SSE 모드로 서버 실행 (REST API 포함)
java -jar build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse
```

## 7. 테스트

### Open WebUI에서 테스트

1. 새 채팅 시작
2. 모델 선택 (예: qwen2.5:14b)
3. 자연어로 요청:

```
할 일 목록 보여줘
Show my todos
```

```
할 일 추가해줘: 보고서 작성
Add todo: Write report
```

```
1번 완료 처리해줘
Complete todo 1
```