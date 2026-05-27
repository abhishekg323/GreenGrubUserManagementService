package com.greengrub.usermanagement.controller;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    private AuthResponse sampleAuth;
    private UserResponse sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = UserResponse.builder()
                .id("user-001")
                .name("John Doe")
                .email("john@example.com")
                .role(UserRole.DONOR)
                .isActive(true)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
        sampleAuth = AuthResponse.builder()
                .token("jwt-token-value")
                .tokenType("Bearer")
                .userId("user-001")
                .email("john@example.com")
                .name("John Doe")
                .role(UserRole.DONOR)
                .build();
    }

    // ── signup ────────────────────────────────────────────────────────────────

    @Test
    void signup_returns201WithBody() {
        when(authService.signup(any(UserCreateRequest.class))).thenReturn(sampleAuth);

        UserCreateRequest req = UserCreateRequest.builder()
                .name("John Doe").email("john@example.com").password("password123")
                .role(UserRole.DONOR).build();

        ResponseEntity<ApiResponse> response = controller.signup(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(sampleAuth);
    }

    @Test
    void signup_delegatesToService() {
        when(authService.signup(any())).thenReturn(sampleAuth);
        UserCreateRequest req = UserCreateRequest.builder()
                .name("X").email("x@y.com").password("pass1234").role(UserRole.RECIPIENT).build();

        controller.signup(req);
        verify(authService).signup(req);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returns200WithBody() {
        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuth);

        LoginRequest req = LoginRequest.builder().email("john@example.com").password("password123").build();
        ResponseEntity<ApiResponse> response = controller.login(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void login_delegatesToService() {
        when(authService.login(any())).thenReturn(sampleAuth);
        LoginRequest req = LoginRequest.builder().email("a@b.com").password("pass").build();

        controller.login(req);
        verify(authService).login(req);
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_validBearerHeader_returns200() {
        when(authService.validateToken("jwt-token-value")).thenReturn(sampleUser);

        ResponseEntity<ApiResponse> response = controller.validateToken("Bearer jwt-token-value");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void validateToken_missingHeader_returns400() {
        ResponseEntity<ApiResponse> response = controller.validateToken(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void validateToken_wrongFormat_returns400() {
        ResponseEntity<ApiResponse> response = controller.validateToken("Token abc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── getCurrentUser ────────────────────────────────────────────────────────

    @Test
    void getCurrentUser_withEmail_returns200() {
        ResponseEntity<ApiResponse> response = controller.getCurrentUser("john@example.com", "user-001", "DONOR");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void getCurrentUser_nullEmail_returns403() {
        ResponseEntity<ApiResponse> response = controller.getCurrentUser(null, null, null);
        assertThat(response.getStatusCodeValue()).isEqualTo(403);
        assertThat(response.getBody().isSuccess()).isFalse();
    }
}
