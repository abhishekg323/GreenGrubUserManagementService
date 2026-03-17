package com.greengrub.usermanagement.mapper;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserMapper
 */
class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
    }

    @Test
    void shouldMapCreateRequestToEntity() {
        // Given
        UserCreateRequest request = UserCreateRequest.builder()
                .name("John Doe")
                .email("john@test.com")
                .password("password123")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .build();

        // When
        User user = userMapper.toEntity(request);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("John Doe");
        assertThat(user.getEmail()).isEqualTo("john@test.com");
        assertThat(user.getPassword()).isEqualTo("password123");
        assertThat(user.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(user.getAddress()).isEqualTo("123 Main St");
        assertThat(user.getRole()).isEqualTo(UserRole.DONOR);
        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void shouldMapEntityToResponse() {
        // Given
        User user = User.builder()
                .id("test-id-123")
                .name("John Doe")
                .email("john@test.com")
                .password("hashedPassword")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        UserResponse response = userMapper.toResponse(user);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("test-id-123");
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@test.com");
        assertThat(response.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(response.getAddress()).isEqualTo("123 Main St");
        assertThat(response.getRole()).isEqualTo(UserRole.DONOR);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateEntityWithAllFields() {
        // Given
        User user = User.builder()
                .id("test-id")
                .name("Old Name")
                .email("old@test.com")
                .phoneNumber("0000000000")
                .address("Old Address")
                .isActive(false)
                .build();

        UserUpdateRequest request = UserUpdateRequest.builder()
                .name("New Name")
                .email("new@test.com")
                .phoneNumber("1111111111")
                .address("New Address")
                .isActive(true)
                .build();

        // When
        userMapper.updateEntity(user, request);

        // Then
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getPhoneNumber()).isEqualTo("1111111111");
        assertThat(user.getAddress()).isEqualTo("New Address");
        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void shouldUpdateEntityWithPartialFields() {
        // Given
        User user = User.builder()
                .id("test-id")
                .name("Old Name")
                .email("old@test.com")
                .phoneNumber("0000000000")
                .address("Old Address")
                .isActive(false)
                .build();

        UserUpdateRequest request = UserUpdateRequest.builder()
                .name("New Name")
                .email("new@test.com")
                .build();

        // When
        userMapper.updateEntity(user, request);

        // Then
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getPhoneNumber()).isEqualTo("0000000000"); // Unchanged
        assertThat(user.getAddress()).isEqualTo("Old Address"); // Unchanged
        assertThat(user.getIsActive()).isFalse(); // Unchanged
    }

    @Test
    void shouldNotUpdateEntityWhenAllFieldsAreNull() {
        // Given
        User user = User.builder()
                .id("test-id")
                .name("Original Name")
                .email("original@test.com")
                .phoneNumber("1234567890")
                .address("Original Address")
                .isActive(true)
                .build();

        UserUpdateRequest request = UserUpdateRequest.builder().build();

        // When
        userMapper.updateEntity(user, request);

        // Then
        assertThat(user.getName()).isEqualTo("Original Name");
        assertThat(user.getEmail()).isEqualTo("original@test.com");
        assertThat(user.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(user.getAddress()).isEqualTo("Original Address");
        assertThat(user.getIsActive()).isTrue();
    }
}
