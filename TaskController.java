package com.taskflow.api.controller;

import com.taskflow.api.dto.request.TaskRequest;
import com.taskflow.api.dto.response.PageResponse;
import com.taskflow.api.dto.response.TaskResponse;
import com.taskflow.domain.entity.TaskPriority;
import com.taskflow.domain.entity.TaskStatus;
import com.taskflow.domain.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(Authentication authentication, @Valid @RequestBody TaskRequest request) {
        return taskService.create(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    public TaskResponse get(Authentication authentication, @PathVariable UUID id) {
        return taskService.get(authentication.getName(), id);
    }

    @GetMapping
    public PageResponse<TaskResponse> list(
            Authentication authentication,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return taskService.list(authentication.getName(), status, priority, dueBefore, dueAfter,
                search, pageable);
    }

    @PutMapping("/{id}")
    public TaskResponse update(Authentication authentication, @PathVariable UUID id,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        taskService.delete(authentication.getName(), id);
    }
}
