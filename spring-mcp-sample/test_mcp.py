#!/usr/bin/env python3
"""MCP Server 로컬 테스트 스크립트"""

import subprocess
import json
import time
import threading
import sys

JAR_PATH = "build/libs/spring-mcp-sample-0.0.1-SNAPSHOT.jar"
JAVA_PATH = "/Users/jaeseoh/Library/Java/JavaVirtualMachines/azul-21.0.8/Contents/Home/bin/java"

def send_request(proc, method, params=None, req_id=None):
    """MCP 요청 전송"""
    request = {"jsonrpc": "2.0", "method": method}
    if params:
        request["params"] = params
    if req_id:
        request["id"] = req_id
    proc.stdin.write(json.dumps(request) + "\n")
    proc.stdin.flush()

def main():
    print("=== MCP Todo Server 테스트 ===\n")

    # 서버 시작
    proc = subprocess.Popen(
        [JAVA_PATH, "-jar", JAR_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    responses = []

    def read_output():
        while True:
            line = proc.stdout.readline()
            if not line:
                break
            responses.append(json.loads(line.strip()))

    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()

    time.sleep(3)  # 서버 시작 대기

    # 1. Initialize
    print("[1] Initialize...")
    send_request(proc, "initialize", {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": "test-client", "version": "1.0"}
    }, req_id=1)
    time.sleep(1)

    # 2. Initialized notification
    send_request(proc, "notifications/initialized", {})
    time.sleep(0.5)

    # 3. List tools
    print("[2] Tools 목록 조회...")
    send_request(proc, "tools/list", {}, req_id=2)
    time.sleep(1)

    # 4. Add todos
    print("[3] Todo 추가: '장보기'...")
    send_request(proc, "tools/call", {
        "name": "add_todo",
        "arguments": {"title": "장보기"}
    }, req_id=3)
    time.sleep(0.5)

    print("[4] Todo 추가: '코드 리뷰'...")
    send_request(proc, "tools/call", {
        "name": "add_todo",
        "arguments": {"title": "코드 리뷰"}
    }, req_id=4)
    time.sleep(0.5)

    # 5. List todos
    print("[5] Todo 목록 조회...")
    send_request(proc, "tools/call", {
        "name": "list_todos",
        "arguments": {}
    }, req_id=5)
    time.sleep(0.5)

    # 6. Complete todo
    print("[6] Todo #1 완료 처리...")
    send_request(proc, "tools/call", {
        "name": "complete_todo",
        "arguments": {"id": 1}
    }, req_id=6)
    time.sleep(0.5)

    # 7. Delete todo
    print("[7] Todo #2 삭제...")
    send_request(proc, "tools/call", {
        "name": "delete_todo",
        "arguments": {"id": 2}
    }, req_id=7)
    time.sleep(0.5)

    # 8. Final list
    print("[8] 최종 Todo 목록...")
    send_request(proc, "tools/call", {
        "name": "list_todos",
        "arguments": {}
    }, req_id=8)
    time.sleep(1)

    proc.terminate()
    reader.join(timeout=1)

    # 결과 출력
    print("\n=== 테스트 결과 ===\n")
    for resp in responses:
        req_id = resp.get("id")
        result = resp.get("result", {})

        if req_id == 1:
            print(f"[1] Initialize: {result.get('serverInfo', {}).get('name')}")
        elif req_id == 2:
            tools = [t["name"] for t in result.get("tools", [])]
            print(f"[2] Tools: {', '.join(tools)}")
        elif req_id in [3, 4, 5, 6, 7, 8]:
            content = result.get("content", [{}])[0].get("text", "N/A")
            print(f"[{req_id}] {content}")

    print("\n테스트 완료!")

if __name__ == "__main__":
    main()
