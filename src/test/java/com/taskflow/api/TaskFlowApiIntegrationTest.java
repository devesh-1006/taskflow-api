package com.taskflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.api.dto.request.LoginRequest;
import com.taskflow.api.dto.request.RegisterRequest;
import com.taskflow.api.dto.request.TaskRequest;
import com.taskflow.domain.entity.TaskPriority;
import com.taskflow.domain.entity.TaskStatus;
import com.taskflow.domain.repository.UserRepository;
import com.taskflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskFlowApiIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void registrationLoginAndTaskEndpointsWorkWithOwnershipIsolation() throws Exception {
        String ownerEmail = email("owner");
        String ownerToken = register(ownerEmail);
        String taskId = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "Write tests", "Cover API behavior", TaskStatus.IN_PROGRESS,
                                TaskPriority.HIGH, LocalDate.now().plusDays(1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(taskId).get("id").asText();
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("status", "IN_PROGRESS")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id));

        String otherUserToken = register(email("other"));
        mockMvc.perform(get("/api/tasks/{id}", id)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ownerEmail.toUpperCase(), PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        String passwordHash = userRepository.findByEmail(ownerEmail)
                .orElseThrow().getPasswordHash();
        assertNotEquals(PASSWORD, passwordHash);
        assertTrue(passwordEncoder.matches(PASSWORD, passwordHash));
    }

    @Test
    void taskCrudAndAllTaskRoutesEnforceOwnerIsolation() throws Exception {
        String ownerToken = register(email("crud-owner"));
        String otherToken = register(email("crud-other"));
        String id = createTask(ownerToken, task("Private task", "private details",
                TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now().plusDays(1)));

        mockMvc.perform(get("/api/tasks/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/{id}", id).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(task("Hijacked", null, TaskStatus.DONE,
                                TaskPriority.HIGH, null))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(put("/api/tasks/{id}", id).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(task("Updated task", "updated details",
                                TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated task"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/tasks/{id}", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("updated details"));
        mockMvc.perform(delete("/api/tasks/{id}", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/{id}", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void taskFiltersSortingPaginationAndInvalidParametersAreHandled() throws Exception {
        String token = register(email("filters"));
        LocalDate today = LocalDate.now();
        createTask(token, task("Alpha planning", "prepare release",
                TaskStatus.TODO, TaskPriority.HIGH, today.plusDays(1)));
        createTask(token, task("Beta review", "review pull request",
                TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, today.plusDays(2)));
        createTask(token, task("Gamma archive", null,
                TaskStatus.DONE, TaskPriority.LOW, today.plusDays(3)));

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("status", "TODO")
                        .param("priority", "HIGH")
                        .param("dueAfter", today.toString())
                        .param("dueBefore", today.plusDays(2).toString())
                        .param("search", "ALPHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Alpha planning"));

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("page", "0").param("size", "1").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Alpha planning"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("page", "1").param("size", "1").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Beta review"));

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(3)));

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(3)));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("size", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("page", "-1").param("size", "1").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].title").value("Alpha planning"));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("sort", "owner.passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A request parameter has an invalid value."));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(token))
                        .param("dueAfter", today.plusDays(3).toString())
                        .param("dueBefore", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dueAfter must be on or before dueBefore."));
    }

    @Test
    void invalidCredentialsDuplicateRegistrationAndMissingOrInvalidTokensFailSafely() throws Exception {
        String email = email("auth");
        String token = register(email);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email.toUpperCase(), PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "IncorrectPassword123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(get("/api/tasks").header("Authorization", bearer("not.a.valid.jwt")))
                .andExpect(status().isUnauthorized());

        String expiredToken = jwtService.generateToken(email, java.time.Instant.now().minusSeconds(3600));
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized());

        String validToken = jwtService.generateToken(email, java.time.Instant.now().minusSeconds(1));
        int signatureStart = validToken.lastIndexOf('.') + 1;
        char firstSignatureCharacter = validToken.charAt(signatureStart);
        String invalidSignatureToken = validToken.substring(0, signatureStart)
                + (firstSignatureCharacter == 'A' ? 'B' : 'A')
                + validToken.substring(signatureStart + 1);
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(invalidSignatureToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(json(task("No auth", null, TaskStatus.TODO, TaskPriority.LOW, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/tasks/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(task("No auth", null, TaskStatus.TODO, TaskPriority.LOW, null))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(task(" ", null, TaskStatus.TODO, TaskPriority.LOW, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists());
        mockMvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request body is invalid or contains an unsupported value."));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String createTask(String token, TaskRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private TaskRequest task(String title, String description, TaskStatus status,
                             TaskPriority priority, LocalDate dueDate) {
        return new TaskRequest(title, description, status, priority, dueDate);
    }

    private String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
