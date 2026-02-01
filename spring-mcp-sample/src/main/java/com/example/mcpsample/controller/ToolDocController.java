package com.example.mcpsample.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool 문서 엔드포인트
 * Swagger처럼 등록된 MCP Tool의 메타데이터를 조회할 수 있습니다.
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolDocController {

    private final ToolCallbackProvider toolCallbackProvider;

    @GetMapping
    public List<Map<String, Object>> listTools() {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(this::toToolDoc)
                .toList();
    }

    private Map<String, Object> toToolDoc(ToolCallback tool) {
        var definition = tool.getToolDefinition();
        return Map.of(
                "name", definition.name(),
                "description", definition.description(),
                "inputSchema", definition.inputSchema()
        );
    }
}