package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.exception.UserStorageException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import com.greengrub.usermanagement.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserMapper userMapper;

    @InjectMocks private AuthService authService;

    private User sampleUser;
    private UserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id("user-001").name("John Doe").email("john@example.com")
                .password("hashed-pass").role(UserRole.DONOR).isActive(true)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        sampleUserResponse = UserResponse.builder()
                .id("user-001").name("John Doe").email("john@example.com")
                .role(UserRole.DONOR).isActive(true)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    // ── signup ────────────────────────────────────────────────────────────────

    @Test
    void signup_newEmail_returnsAuthResponse() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userMapper.toEntity(any(UserCreateRequest.class))).thenReturn(sampleUser);
        when(passwordEncoder.encode(any())).thenReturn("hashed-pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("jwt-token");

        UserCreateRequest req = UserCreateRequest.builder()
                .name("John Doe").email("john@example.com").password("pass1234")
                .role(UserRole.DONOR).build();

        AuthResponse result = authService.signup(req);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void signup_duplicateEmail_throwsUserAlreadyExists() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        UserCreateRequest req = UserCreateRequest.builder()
                .name("John").email("john@example.com").password("pass1234")
                .role(UserRole.DONOR).build();

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsAuthResponse() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("pass1234", "hashed-pass")).thenReturn(true);
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("jwt-token");

        LoginRequest req = LoginRequest.builder().email("john@example.com").password("pass1234").build();
        AuthResponse result = authService.login(req);

        assertThat(result.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        LoginRequest req = LoginRequest.builder().email("missing@example.com").password("pass").build();

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void login_inactiveUser_throwsInvalidPasswordException() {
        sampleUser.setIsActive(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));

        LoginRequest req = LoginRequest.builder().email("john@example.com").password("pass1234").build();

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_wrongPassword_throwsInvalidPasswordException() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongpass", "hashed-pass")).thenReturn(false);

        LoginRequest req = LoginRequest.builder().email("john@example.com").password("wrongpass").build();

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidPasswordException.class);
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_validToken_returnsUserResponse() {
        when(jwtUtil.extractUsername("valid-token")).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);

        UserResponse result = authService.validateToken("valid-token");

        assertThat(result.getId()).isEqualTo("user-001");
    }

    @Test
    void validateToken_userNotFound_throwsUserNotFoundException() {
        when(jwtUtil.extractUsername("token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.validateToken("token"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void validateToken_inactiveUser_throwsInvalidPasswordException() {
        sampleUser.setIsActive(false);
        when(jwtUtil.extractUsername("token")).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.validateToken("token"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    // ── resilience exception paths ────────────────────────────────────────────

    @Test
    void existsByEmail_repositoryThrows_wrapsInUserStorageException() {
        when(userRepository.existsByEmail("john@example.com")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> authService.existsByEmail("john@example.com"))
                .isInstanceOf(UserStorageException.class)
                .hasMessageContaining("Failed to check email existence");
    }

    @Test
    void saveUser_repositoryThrows_wrapsInUserStorageException() {
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> authService.saveUser(sampleUser))
                .isInstanceOf(UserStorageException.class)
                .hasMessageContaining("Failed to save user");
    }

    @Test
    void findUserByEmail_repositoryThrows_wrapsInUserStorageException() {
        when(userRepository.findByEmail("john@example.com")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> authService.findUserByEmail("john@example.com"))
                .isInstanceOf(UserStorageException.class)
                .hasMessageContaining("Failed to fetch user by email");
    }
}
