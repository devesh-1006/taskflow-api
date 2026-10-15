package com.taskflow.domain.service;

import com.taskflow.api.dto.request.TaskRequest;
import com.taskflow.api.dto.response.PageResponse;
import com.taskflow.api.dto.response.TaskResponse;
import com.taskflow.domain.entity.AppUser;
import com.taskflow.domain.entity.Task;
import com.taskflow.domain.entity.TaskPriority;
import com.taskflow.domain.entity.TaskStatus;
import com.taskflow.domain.repository.TaskRepository;
import com.taskflow.domain.repository.UserRepository;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "status", "priority", "dueDate", "createdAt", "updatedAt");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(String email, TaskRequest request) {
        AppUser owner = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Task task = new Task(request.title(), request.description(), request.status(),
                request.priority(), request.dueDate(), owner);
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(String email, UUID id) {
        return toResponse(findOwnedTask(email, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(
            String email,
            TaskStatus status,
            TaskPriority priority,
            LocalDate dueBefore,
            LocalDate dueAfter,
            String search,
            Pageable pageable
    ) {
        if (dueBefore != null && dueAfter != null && dueAfter.isAfter(dueBefore)) {
            throw new BadRequestException("dueAfter must be on or before dueBefore.");
        }
        Pageable boundedPageable = bounded(pageable);
        Specification<Task> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("owner").get("email"), normalizeEmail(email));

        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        if (priority != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("priority"), priority));
        }
        if (dueBefore != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.<LocalDate>get("dueDate"), dueBefore));
        }
        if (dueAfter != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), dueAfter));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)));
        }
        Page<TaskResponse> result = taskRepository.findAll(specification, boundedPageable)
                .map(TaskService::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    public TaskResponse update(String email, UUID id, TaskRequest request) {
        Task task = findOwnedTask(email, id);
        task.update(request.title(), request.description(), request.status(),
                request.priority(), request.dueDate());
        return toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(String email, UUID id) {
        taskRepository.delete(findOwnedTask(email, id));
    }

    private Task findOwnedTask(String email, UUID id) {
        return taskRepository.findByIdAndOwnerEmail(id, normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
    }

    private Pageable bounded(Pageable pageable) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort field: " + order.getProperty());
            }
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
