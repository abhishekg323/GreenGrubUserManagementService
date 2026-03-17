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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController
 * Tests login, signup, and token validation
 */
@SpringBootTest
class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private UserCreateRequest signupRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Clean database before each test
        userRepository.deleteAll();

        signupRequest = UserCreateRequest.builder()
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
    void shouldSignupSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("john@test.com"))
                .andExpect(jsonPath("$.data.name").value("John Doe"))
                .andExpect(jsonPath("$.data.role").value("DONOR"));
    }

    @Test
    void shouldFailSignupWithInvalidData() throws Exception {
        UserCreateRequest invalidRequest = UserCreateRequest.builder()
                .email("invalid-email")
                .password("short")
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    void shouldFailSignupWithExistingEmail() throws Exception {
        // First signup
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // Try to signup again with same email
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User Already Exists"));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        // First signup
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // Then login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("john@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("john@test.com"));
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        // First signup
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // Try to login with wrong password
        LoginRequest loginRequest = LoginRequest.builder()
                .email("john@test.com")
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Password"));
    }

    @Test
    void shouldFailLoginWithNonExistentUser() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    void shouldValidateTokenSuccessfully() throws Exception {
        // First signup to get token
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> authData = (Map<String, Object>) apiResponse.getData();
        String token = (String) authData.get("token");

        // Validate token
        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"))
                .andExpect(jsonPath("$.data.email").value("john@test.com"));
    }

    @Test
    void shouldFailValidateTokenWithoutAuthHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid authorization header format"));
    }

    @Test
    void shouldFailValidateTokenWithInvalidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "InvalidFormat token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid authorization header format"));
    }

    @Test
    void shouldGetCurrentUserWithValidToken() throws Exception {
        // First signup to get token
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> authData = (Map<String, Object>) apiResponse.getData();
        String token = (String) authData.get("token");

        // Get current user
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Current user retrieved"));
    }

    @Test
    void shouldFailGetCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
