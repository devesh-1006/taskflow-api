package com.taskflow.domain.service;

import com.taskflow.domain.entity.Task;
import com.taskflow.domain.repository.TaskRepository;
import com.taskflow.domain.repository.UserRepository;
import com.taskflow.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TaskService taskService = new TaskService(taskRepository, userRepository);

    @Test
    void getLooksUpTaskUsingAuthenticatedOwner() {
        UUID taskId = UUID.randomUUID();
        Task task = mock(Task.class);
        when(taskRepository.findByIdAndOwnerEmail(taskId, "user@example.com"))
                .thenReturn(Optional.of(task));
        when(task.getId()).thenReturn(taskId);

        assertEquals(taskId, taskService.get("USER@example.com", taskId).id());
        verify(taskRepository).findByIdAndOwnerEmail(taskId, "user@example.com");
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void getHidesTasksThatDoNotBelongToAuthenticatedOwner() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndOwnerEmail(taskId, "user@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.get("user@example.com", taskId));
        verify(taskRepository).findByIdAndOwnerEmail(taskId, "user@example.com");
        verifyNoMoreInteractions(taskRepository);
    }
}
