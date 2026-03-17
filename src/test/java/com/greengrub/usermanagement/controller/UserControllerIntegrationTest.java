package com.greengrub.usermanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserController
 * Tests the complete flow from controller through service to repository
 */
@SpringBootTest
class UserControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private UserCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // Clean database before each test
        userRepository.deleteAll();

        createRequest = UserCreateRequest.builder()
                .name("John Doe")
                .email("john@test.com")
                .password("password123")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .build();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("john@test.com"))
                .andExpect(jsonPath("$.data.name").value("John Doe"));
    }

    @Test
    void shouldReturnValidationErrorWhenCreatingUserWithInvalidData() throws Exception {
        UserCreateRequest invalidRequest = UserCreateRequest.builder().build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    void shouldReturnConflictWhenCreatingUserWithExistingEmail() throws Exception {
        // First create a user
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Try to create another user with same email
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User Already Exists"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldGetUserById() throws Exception {
        // First create a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Get user by ID
        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("john@test.com"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldGetAllUsers() throws Exception {
        // Create two users
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        UserCreateRequest secondUser = UserCreateRequest.builder()
                .name("Jane Doe")
                .email("jane@test.com")
                .password("password456")
                .phoneNumber("9876543210")
                .address("456 Oak St")
                .role(UserRole.RECIPIENT)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUser)))
                .andExpect(status().isCreated());

        // Get all users
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldGetActiveUsers() throws Exception {
        // Create a user
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Get active users
        mockMvc.perform(get("/api/v1/users?active=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void shouldGetUsersByRole() throws Exception {
        // Create users with different roles
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        UserCreateRequest recipient = UserCreateRequest.builder()
                .name("Jane Doe")
                .email("jane@test.com")
                .password("password456")
                .phoneNumber("9876543210")
                .role(UserRole.RECIPIENT)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recipient)))
                .andExpect(status().isCreated());

        // Get users by role
        mockMvc.perform(get("/api/v1/users?role=DONOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("DONOR"));
    }

    @Test
    void shouldSearchUsersByName() throws Exception {
        // Create a user
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Search by name
        mockMvc.perform(get("/api/v1/users/search?name=John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void shouldUpdateUser() throws Exception {
        // First create a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Update user
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .name("John Updated")
                .build();

        mockMvc.perform(put("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"));
    }

    @Test
    void shouldUpdatePassword() throws Exception {
        // First create a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Update password
        PasswordUpdateRequest passwordRequest = PasswordUpdateRequest.builder()
                .oldPassword("password123")
                .newPassword("newPassword456")
                .build();

        mockMvc.perform(put("/api/v1/users/" + userId + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }

    @Test
    void shouldActivateUser() throws Exception {
        // First create and deactivate a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Deactivate first
        mockMvc.perform(put("/api/v1/users/" + userId + "/deactivate"))
                .andExpect(status().isOk());

        // Then activate
        mockMvc.perform(put("/api/v1/users/" + userId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User activated successfully"));
    }

    @Test
    void shouldDeactivateUser() throws Exception {
        // First create a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Deactivate user
        mockMvc.perform(put("/api/v1/users/" + userId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deactivated successfully"));
    }

    @Test
    void shouldDeleteUser() throws Exception {
        // First create a user
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) apiResponse.getData();
        String userId = (String) userData.get("id");

        // Delete user
        mockMvc.perform(delete("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        // Verify user is soft-deleted (deactivated, not actually removed)
        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void shouldCheckEmailExists() throws Exception {
        // Create a user
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Check if email exists
        mockMvc.perform(get("/api/v1/users/check-email?email=john@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.message").value("Email already exists"));

        // Check non-existent email
        mockMvc.perform(get("/api/v1/users/check-email?email=nonexistent@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.message").value("Email is available"));
    }

    @Test
    void shouldGetUserStats() throws Exception {
        // Create users with different roles
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        UserCreateRequest recipient = UserCreateRequest.builder()
                .name("Jane Doe")
                .email("jane@test.com")
                .password("password456")
                .phoneNumber("9876543210")
                .role(UserRole.RECIPIENT)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recipient)))
                .andExpect(status().isCreated());

        // Get stats
        mockMvc.perform(get("/api/v1/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.activeUsers").value(2))
                .andExpect(jsonPath("$.data.donors").value(1))
                .andExpect(jsonPath("$.data.recipients").value(1));
    }
}
