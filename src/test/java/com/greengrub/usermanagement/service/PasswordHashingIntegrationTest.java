package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for password hashing functionality
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordHashingIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    private UserCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        createRequest = UserCreateRequest.builder()
                .name("John Doe")
                .email("john@test.com")
                .password("plainPassword123")
                .phoneNumber("1234567890")
                .role(UserRole.DONOR)
                .build();
    }

    @Test
    void shouldHashPasswordWhenCreatingUser() {
        // When
        UserResponse response = userService.createUser(createRequest);

        // Then
        User savedUser = userRepository.findById(response.getId()).orElseThrow();

        // Password should be hashed (not plain text)
        assertThat(savedUser.getPassword()).isNotEqualTo("plainPassword123");

        // Password should be a valid BCrypt hash (starts with $2a$ or $2b$)
        assertThat(savedUser.getPassword()).startsWith("$2");

        // BCrypt hashes are 60 characters long
        assertThat(savedUser.getPassword()).hasSize(60);

        // Should be able to verify the password
        assertThat(passwordEncoder.matches("plainPassword123", savedUser.getPassword())).isTrue();
    }

    @Test
    void shouldVerifyCorrectPassword() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When
        boolean isValid = userService.verifyPassword(user.getId(), "plainPassword123");

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectIncorrectPassword() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When
        boolean isValid = userService.verifyPassword(user.getId(), "wrongPassword");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldUpdatePasswordWithValidOldPassword() {
        // Given
        UserResponse user = userService.createUser(createRequest);
        String oldPassword = "plainPassword123";
        String newPassword = "newSecurePassword456";

        // When
        userService.updatePassword(user.getId(), oldPassword, newPassword);

        // Then
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();

        // Old password should not work
        assertThat(passwordEncoder.matches(oldPassword, updatedUser.getPassword())).isFalse();

        // New password should work
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue();

        // Verify through service
        assertThat(userService.verifyPassword(user.getId(), newPassword)).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenUpdatingPasswordWithInvalidOldPassword() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When & Then
        assertThatThrownBy(() ->
            userService.updatePassword(user.getId(), "wrongOldPassword", "newPassword123")
        )
            .isInstanceOf(InvalidPasswordException.class)
            .hasMessageContaining("Old password is incorrect");
    }

    @Test
    void shouldThrowExceptionWhenNewPasswordIsTooShort() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When & Then
        assertThatThrownBy(() ->
            userService.updatePassword(user.getId(), "plainPassword123", "short")
        )
            .isInstanceOf(InvalidPasswordException.class)
            .hasMessageContaining("at least 8 characters");
    }

    @Test
    void shouldResetPasswordAsAdmin() {
        // Given
        UserResponse user = userService.createUser(createRequest);
        String newPassword = "adminResetPassword123";

        // When
        userService.resetPassword(user.getId(), newPassword);

        // Then
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();

        // Old password should not work
        assertThat(passwordEncoder.matches("plainPassword123", updatedUser.getPassword())).isFalse();

        // New password should work
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenResettingPasswordForNonExistentUser() {
        // When & Then
        assertThatThrownBy(() ->
            userService.resetPassword("non-existent-id", "newPassword123")
        )
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldThrowExceptionWhenResetPasswordIsTooShort() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When & Then
        assertThatThrownBy(() ->
            userService.resetPassword(user.getId(), "short")
        )
            .isInstanceOf(InvalidPasswordException.class)
            .hasMessageContaining("at least 8 characters");
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        // Given - Create two users with same password
        UserCreateRequest request1 = UserCreateRequest.builder()
                .name("User One")
                .email("user1@test.com")
                .password("samePassword123")
                .phoneNumber("1111111111")
                .role(UserRole.DONOR)
                .build();

        UserCreateRequest request2 = UserCreateRequest.builder()
                .name("User Two")
                .email("user2@test.com")
                .password("samePassword123")
                .phoneNumber("2222222222")
                .role(UserRole.RECIPIENT)
                .build();

        // When
        UserResponse user1 = userService.createUser(request1);
        UserResponse user2 = userService.createUser(request2);

        // Then
        User savedUser1 = userRepository.findById(user1.getId()).orElseThrow();
        User savedUser2 = userRepository.findById(user2.getId()).orElseThrow();

        // Hashes should be different due to different salts
        assertThat(savedUser1.getPassword()).isNotEqualTo(savedUser2.getPassword());

        // But both should verify with the same password
        assertThat(passwordEncoder.matches("samePassword123", savedUser1.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("samePassword123", savedUser2.getPassword())).isTrue();
    }

    @Test
    void shouldNotExposePasswordInResponse() {
        // When
        UserResponse response = userService.createUser(createRequest);

        // Then - UserResponse DTO should not contain password field
        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("john@test.com");
        // Password is not in UserResponse DTO, so it's automatically excluded
    }

    @Test
    void shouldHandlePasswordUpdateMultipleTimes() {
        // Given
        UserResponse user = userService.createUser(createRequest);

        // When - Update password multiple times
        userService.updatePassword(user.getId(), "plainPassword123", "password2");
        userService.updatePassword(user.getId(), "password2", "password3");
        userService.updatePassword(user.getId(), "password3", "finalPassword123");

        // Then
        assertThat(userService.verifyPassword(user.getId(), "finalPassword123")).isTrue();
        assertThat(userService.verifyPassword(user.getId(), "plainPassword123")).isFalse();
        assertThat(userService.verifyPassword(user.getId(), "password2")).isFalse();
        assertThat(userService.verifyPassword(user.getId(), "password3")).isFalse();
    }
}
