package com.example.mcpsample.service;

import com.example.mcpsample.domain.Todo;
import com.example.mcpsample.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    @Tool(name = "add_todo", description = "Add a new todo item with the given title")
    @Transactional
    public String addTodo(@ToolParam(description = "The title of the todo item", required = true) String title) {
        Todo todo = Todo.builder()
                .title(title)
                .completed(false)
                .build();
        Todo saved = todoRepository.save(todo);
        return String.format("Todo added successfully. ID: %d, Title: %s", saved.getId(), title);
    }

    @Tool(name = "list_todos", description = "List all todo items")
    @Transactional(readOnly = true)
    public List<Todo> listTodos() {
        return todoRepository.findAll();
    }

    @Tool(name = "complete_todo", description = "Mark a todo item as completed")
    @Transactional
    public String completeTodo(@ToolParam(description = "The ID of the todo item to complete", required = true) Long id) {
        return todoRepository.findById(id)
                .map(todo -> {
                    todo.complete();
                    return String.format("Todo %d marked as completed. Title: %s", id, todo.getTitle());
                })
                .orElse(String.format("Todo with ID %d not found", id));
    }

    @Tool(name = "delete_todo", description = "Delete a todo item")
    @Transactional
    public String deleteTodo(@ToolParam(description = "The ID of the todo item to delete", required = true) Long id) {
        return todoRepository.findById(id)
                .map(todo -> {
                    todoRepository.delete(todo);
                    return String.format("Todo %d deleted successfully. Title: %s", id, todo.getTitle());
                })
                .orElse(String.format("Todo with ID %d not found", id));
    }
}
