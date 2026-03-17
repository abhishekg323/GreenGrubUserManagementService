package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserCreateRequest createRequest;
    private UserUpdateRequest updateRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
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

        createRequest = UserCreateRequest.builder()
                .name("John Doe")
                .email("john@test.com")
                .password("password123")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .build();

        updateRequest = UserUpdateRequest.builder()
                .name("John Updated")
                .email("john.updated@test.com")
                .build();

        userResponse = UserResponse.builder()
                .id("test-id-123")
                .name("John Doe")
                .email("john@test.com")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        User userWithPlainPassword = User.builder()
                .name("John Doe")
                .email("john@test.com")
                .password("password123") // Plain password from request
                .phoneNumber("1234567890")
                .address("123 Main St")
                .role(UserRole.DONOR)
                .isActive(true)
                .build();

        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(userMapper.toEntity(createRequest)).thenReturn(userWithPlainPassword);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.createUser(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@test.com");
        verify(userRepository).existsByEmail(createRequest.getEmail());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingUserWithExistingEmail() {
        // Given
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("john@test.com");

        verify(userRepository).existsByEmail(createRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldGetUserByIdSuccessfully() {
        // Given
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.getUserById("test-id-123");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("test-id-123");
        verify(userRepository).findById("test-id-123");
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundById() {
        // Given
        when(userRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserById("non-existent-id"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("non-existent-id");

        verify(userRepository).findById("non-existent-id");
    }

    @Test
    void shouldGetUserByEmailSuccessfully() {
        // Given
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.getUserByEmail("john@test.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@test.com");
        verify(userRepository).findByEmail("john@test.com");
    }

    @Test
    void shouldGetAllUsers() {
        // Given
        List<User> users = Arrays.asList(testUser);
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.getAllUsers();

        // Then
        assertThat(result).hasSize(1);
        verify(userRepository).findAll();
    }

    @Test
    void shouldGetActiveUsers() {
        // Given
        List<User> activeUsers = Arrays.asList(testUser);
        when(userRepository.findByIsActiveTrue()).thenReturn(activeUsers);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.getActiveUsers();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(userRepository).findByIsActiveTrue();
    }

    @Test
    void shouldGetUsersByRole() {
        // Given
        List<User> donors = Arrays.asList(testUser);
        when(userRepository.findByRole(UserRole.DONOR)).thenReturn(donors);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.getUsersByRole(UserRole.DONOR);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(UserRole.DONOR);
        verify(userRepository).findByRole(UserRole.DONOR);
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        // Given
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("john.updated@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.updateUser("test-id-123", updateRequest);

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findById("test-id-123");
        verify(userMapper).updateEntity(testUser, updateRequest);
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithExistingEmail() {
        // Given
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("john.updated@test.com")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.updateUser("test-id-123", updateRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("john.updated@test.com");

        verify(userRepository).findById("test-id-123");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        // Given
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.deleteUser("test-id-123");

        // Then
        verify(userRepository).findById("test-id-123");
        verify(userRepository).save(testUser);
        assertThat(testUser.getIsActive()).isFalse();
    }

    @Test
    void shouldPermanentlyDeleteUser() {
        // Given
        when(userRepository.existsById("test-id-123")).thenReturn(true);

        // When
        userService.permanentlyDeleteUser("test-id-123");

        // Then
        verify(userRepository).existsById("test-id-123");
        verify(userRepository).deleteById("test-id-123");
    }

    @Test
    void shouldActivateUser() {
        // Given
        testUser.setIsActive(false);
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.activateUser("test-id-123");

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findById("test-id-123");
        verify(userRepository).save(testUser);
        assertThat(testUser.getIsActive()).isTrue();
    }

    @Test
    void shouldDeactivateUser() {
        // Given
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // When
        UserResponse result = userService.deactivateUser("test-id-123");

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findById("test-id-123");
        verify(userRepository).save(testUser);
        assertThat(testUser.getIsActive()).isFalse();
    }

    @Test
    void shouldCheckIfEmailExists() {
        // Given
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        // When
        boolean exists = userService.existsByEmail("john@test.com");

        // Then
        assertThat(exists).isTrue();
        verify(userRepository).existsByEmail("john@test.com");
    }

    @Test
    void shouldSearchUsersByName() {
        // Given
        List<User> users = Arrays.asList(testUser);
        when(userRepository.findByNameContainingIgnoreCase("john")).thenReturn(users);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.searchUsersByName("john");

        // Then
        assertThat(result).hasSize(1);
        verify(userRepository).findByNameContainingIgnoreCase("john");
    }

    @Test
    void shouldGetActiveDonors() {
        // Given
        List<User> donors = Arrays.asList(testUser);
        when(userRepository.findAllActiveDonors(UserRole.DONOR)).thenReturn(donors);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.getActiveDonors();

        // Then
        assertThat(result).hasSize(1);
        verify(userRepository).findAllActiveDonors(UserRole.DONOR);
    }

    @Test
    void shouldGetActiveRecipients() {
        // Given
        testUser.setRole(UserRole.RECIPIENT);
        List<User> recipients = Arrays.asList(testUser);
        when(userRepository.findAllActiveRecipients(UserRole.RECIPIENT)).thenReturn(recipients);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // When
        List<UserResponse> result = userService.getActiveRecipients();

        // Then
        assertThat(result).hasSize(1);
        verify(userRepository).findAllActiveRecipients(UserRole.RECIPIENT);
    }

    @Test
    void shouldCountUsersByRole() {
        // Given
        when(userRepository.countByRole(UserRole.DONOR)).thenReturn(5L);

        // When
        long count = userService.countUsersByRole(UserRole.DONOR);

        // Then
        assertThat(count).isEqualTo(5L);
        verify(userRepository).countByRole(UserRole.DONOR);
    }

    @Test
    void shouldCountActiveUsers() {
        // Given
        when(userRepository.countActiveUsers()).thenReturn(10L);

        // When
        long count = userService.countActiveUsers();

        // Then
        assertThat(count).isEqualTo(10L);
        verify(userRepository).countActiveUsers();
    }
}
