package com.example.mcpsample.config;

import com.example.mcpsample.service.TodoService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider todoTools(TodoService todoService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(todoService)
                .build();
    }
}
