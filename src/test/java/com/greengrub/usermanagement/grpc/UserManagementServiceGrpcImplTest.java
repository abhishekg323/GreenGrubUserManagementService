package com.greengrub.usermanagement.grpc;

import com.greengrub.proto.users.UserByIdRequest;
import com.greengrub.proto.users.GetUserByEmailRequest;
import com.greengrub.proto.users.CreateUserRequest;
import com.greengrub.proto.users.UpdateUserRequest;
import com.greengrub.proto.users.DeleteUserResponse;
import com.greengrub.proto.users.VerifyCredentialsRequest;
import com.greengrub.proto.users.VerifyCredentialsResponse;
import com.greengrub.proto.users.ListUsersRequest;
import com.greengrub.proto.users.ListUsersResponse;
import com.greengrub.proto.users.UserResponse;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.UserService;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserManagementServiceGrpcImpl
 * Tests all 9 gRPC operations with mocked dependencies
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceGrpcImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StreamObserver<UserResponse> userResponseObserver;

    @Mock
    private StreamObserver<DeleteUserResponse> deleteUserResponseObserver;

    @Mock
    private StreamObserver<VerifyCredentialsResponse> verifyCredentialsResponseObserver;

    @Mock
    private StreamObserver<ListUsersResponse> listUsersResponseObserver;

    @Captor
    private ArgumentCaptor<UserResponse> userResponseCaptor;

    @Captor
    private ArgumentCaptor<DeleteUserResponse> deleteUserResponseCaptor;

    @Captor
    private ArgumentCaptor<VerifyCredentialsResponse> verifyCredentialsResponseCaptor;

    @Captor
    private ArgumentCaptor<ListUsersResponse> listUsersResponseCaptor;

    @Captor
    private ArgumentCaptor<StatusRuntimeException> exceptionCaptor;

    @InjectMocks
    private UserManagementServiceGrpcImpl grpcService;

    private User testUser;
    private static final String USER_ID = "test-user-id";
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD = "hashedPassword123";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(USER_ID)
                .name("Test User")
                .email(USER_EMAIL)
                .password(USER_PASSWORD)
                .phoneNumber("1234567890")
                .address("123 Test St")
                .role(UserRole.DONOR)
                .isActive(true)
                .build();
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== GetUser Tests ====================

    @Test
    void getUser_Success() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(USER_ID)
                .build();

        when(userService.getUserEntityById(USER_ID)).thenReturn(testUser);

        // Act
        grpcService.getUser(request, userResponseObserver);

        // Assert
        verify(userService).getUserEntityById(USER_ID);
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();
        verify(userResponseObserver, never()).onError(any());

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getUserId()).isEqualTo(USER_ID);
        assertThat(response.getUser().getName()).isEqualTo("Test User");
        assertThat(response.getUser().getEmail()).isEqualTo(USER_EMAIL);
        assertThat(response.getUser().getIsActive()).isTrue();
        assertThat(response.getUser().getRole()).isEqualTo(com.greengrub.proto.users.UserRole.DONOR);
    }

    @Test
    void getUser_NotFound() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId("non-existent-id")
                .build();

        when(userService.getUserEntityById(anyString()))
                .thenThrow(new UserNotFoundException("non-existent-id"));

        // Act
        grpcService.getUser(request, userResponseObserver);

        // Assert
        verify(userResponseObserver).onError(exceptionCaptor.capture());
        verify(userResponseObserver, never()).onNext(any());
        verify(userResponseObserver, never()).onCompleted();

        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(exception.getMessage()).contains("non-existent-id");
    }

    // ==================== GetUserByEmail Tests ====================

    @Test
    void getUserByEmail_Success() {
        // Arrange
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail(USER_EMAIL)
                .build();

        when(userService.getUserEntityByEmail(USER_EMAIL)).thenReturn(testUser);

        // Act
        grpcService.getUserByEmail(request, userResponseObserver);

        // Assert
        verify(userService).getUserEntityByEmail(USER_EMAIL);
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getEmail()).isEqualTo(USER_EMAIL);
        assertThat(response.getUser().getName()).isEqualTo("Test User");
    }

    @Test
    void getUserByEmail_NotFound() {
        // Arrange
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail("nonexistent@example.com")
                .build();

        when(userService.getUserEntityByEmail(anyString()))
                .thenThrow(new UserNotFoundException("nonexistent@example.com"));

        // Act
        grpcService.getUserByEmail(request, userResponseObserver);

        // Assert
        verify(userResponseObserver).onError(exceptionCaptor.capture());
        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ==================== CreateUser Tests ====================

    @Test
    void createUser_Success() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("New User")
                .setEmail("new@example.com")
                .setPassword("plainPassword123")
                .setPhoneNumber("9876543210")
                .setAddress("456 New St")
                .setRole(com.greengrub.proto.users.UserRole.RECIPIENT)
                .build();

        when(passwordEncoder.encode("plainPassword123")).thenReturn("hashedPassword");
        when(userService.createUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("new-user-id");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return user;
        });

        // Act
        grpcService.createUser(request, userResponseObserver);

        // Assert
        verify(passwordEncoder).encode("plainPassword123");
        verify(userService, times(1)).createUser(argThat((User user) ->
            user.getEmail().equals("new@example.com")));
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getName()).isEqualTo("New User");
        assertThat(response.getUser().getEmail()).isEqualTo("new@example.com");
        assertThat(response.getUser().getRole()).isEqualTo(com.greengrub.proto.users.UserRole.RECIPIENT);
    }

    @Test
    void createUser_MissingRequiredFields() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("")
                .setEmail("test@example.com")
                .setPassword("password")
                .build();

        // Act
        grpcService.createUser(request, userResponseObserver);

        // Assert
        verify(userResponseObserver).onError(exceptionCaptor.capture());
        verify(userService, never()).createUser(argThat((User user) -> user != null));

        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).contains("required");
    }

    @Test
    void createUser_DuplicateEmail() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("Duplicate User")
                .setEmail("existing@example.com")
                .setPassword("password123")
                .build();

        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userService.createUser(argThat((User user) -> user != null)))
                .thenThrow(new UserAlreadyExistsException("User with email existing@example.com already exists"));

        // Act
        grpcService.createUser(request, userResponseObserver);

        // Assert
        verify(userResponseObserver).onError(exceptionCaptor.capture());
        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(exception.getMessage()).contains("already exists");
    }

    // ==================== UpdateUser Tests ====================

    @Test
    void updateUser_PartialUpdate_Success() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId(USER_ID)
                .setName("Updated Name")
                .setPhoneNumber("9999999999")
                .build();

        when(userService.getUserEntityById(USER_ID)).thenReturn(testUser);
        when(userService.updateUser(argThat((User user) -> user != null))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        grpcService.updateUser(request, userResponseObserver);

        // Assert
        verify(userService).getUserEntityById(USER_ID);
        verify(userService, times(1)).updateUser(argThat((User user) -> user != null));
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getName()).isEqualTo("Updated Name");
        assertThat(response.getUser().getPhoneNumber()).isEqualTo("9999999999");
    }

    @Test
    void updateUser_UserNotFound() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("non-existent")
                .setName("New Name")
                .build();

        when(userService.getUserEntityById(anyString()))
                .thenThrow(new UserNotFoundException("non-existent"));

        // Act
        grpcService.updateUser(request, userResponseObserver);

        // Assert
        verify(userResponseObserver).onError(exceptionCaptor.capture());
        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ==================== DeleteUser Tests ====================

    @Test
    void deleteUser_Success() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(USER_ID)
                .build();

        doNothing().when(userService).deleteUser(USER_ID);

        // Act
        grpcService.deleteUser(request, deleteUserResponseObserver);

        // Assert
        verify(userService).deleteUser(USER_ID);
        verify(deleteUserResponseObserver).onNext(deleteUserResponseCaptor.capture());
        verify(deleteUserResponseObserver).onCompleted();

        DeleteUserResponse response = deleteUserResponseCaptor.getValue();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");
    }

    @Test
    void deleteUser_NotFound() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId("non-existent")
                .build();

        doThrow(new RuntimeException("User not found"))
                .when(userService).deleteUser(anyString());

        // Act
        grpcService.deleteUser(request, deleteUserResponseObserver);

        // Assert
        verify(deleteUserResponseObserver).onError(exceptionCaptor.capture());
        StatusRuntimeException exception = exceptionCaptor.getValue();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    // ==================== ActivateUser Tests ====================

    @Test
    void activateUser_Success() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(USER_ID)
                .build();

        testUser.deactivate();
        when(userService.getUserEntityById(USER_ID)).thenReturn(testUser);
        when(userService.updateUser(argThat((User user) -> user != null))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        grpcService.activateUser(request, userResponseObserver);

        // Assert
        verify(userService).getUserEntityById(USER_ID);
        verify(userService).updateUser(any(User.class));
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getIsActive()).isTrue();
    }

    // ==================== DeactivateUser Tests ====================

    @Test
    void deactivateUser_Success() {
        // Arrange
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(USER_ID)
                .build();

        when(userService.getUserEntityById(USER_ID)).thenReturn(testUser);
        when(userService.updateUser(argThat((User user) -> user != null))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        grpcService.deactivateUser(request, userResponseObserver);

        // Assert
        verify(userService).getUserEntityById(USER_ID);
        verify(userService, times(1)).updateUser(argThat((User user) -> user != null && !user.getIsActive()));
        verify(userResponseObserver).onNext(userResponseCaptor.capture());
        verify(userResponseObserver).onCompleted();

        UserResponse response = userResponseCaptor.getValue();
        assertThat(response.getUser().getIsActive()).isFalse();
    }

    // ==================== VerifyCredentials Tests ====================

    @Test
    void verifyCredentials_ValidCredentials() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail(USER_EMAIL)
                .setPassword("plainPassword")
                .build();

        when(userService.getUserEntityByEmail(USER_EMAIL)).thenReturn(testUser);
        when(passwordEncoder.matches("plainPassword", USER_PASSWORD)).thenReturn(true);

        // Act
        grpcService.verifyCredentials(request, verifyCredentialsResponseObserver);

        // Assert
        verify(userService).getUserEntityByEmail(USER_EMAIL);
        verify(passwordEncoder).matches("plainPassword", USER_PASSWORD);
        verify(verifyCredentialsResponseObserver).onNext(verifyCredentialsResponseCaptor.capture());
        verify(verifyCredentialsResponseObserver).onCompleted();

        VerifyCredentialsResponse response = verifyCredentialsResponseCaptor.getValue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.hasUser()).isTrue();
        assertThat(response.getUser().getEmail()).isEqualTo(USER_EMAIL);
        assertThat(response.getMessage()).contains("valid");
    }

    @Test
    void verifyCredentials_InvalidPassword() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail(USER_EMAIL)
                .setPassword("wrongPassword")
                .build();

        when(userService.getUserEntityByEmail(USER_EMAIL)).thenReturn(testUser);
        when(passwordEncoder.matches("wrongPassword", USER_PASSWORD)).thenReturn(false);

        // Act
        grpcService.verifyCredentials(request, verifyCredentialsResponseObserver);

        // Assert
        verify(passwordEncoder).matches("wrongPassword", USER_PASSWORD);
        verify(verifyCredentialsResponseObserver).onNext(verifyCredentialsResponseCaptor.capture());
        verify(verifyCredentialsResponseObserver).onCompleted();

        VerifyCredentialsResponse response = verifyCredentialsResponseCaptor.getValue();
        assertThat(response.getValid()).isFalse();
        assertThat(response.getMessage()).contains("Invalid password");
    }

    @Test
    void verifyCredentials_UserNotFound_DoesNotRevealUserExistence() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail("nonexistent@example.com")
                .setPassword("somePassword")
                .build();

        when(userService.getUserEntityByEmail(anyString()))
                .thenThrow(new RuntimeException("User not found"));

        // Act
        grpcService.verifyCredentials(request, verifyCredentialsResponseObserver);

        // Assert
        verify(verifyCredentialsResponseObserver).onNext(verifyCredentialsResponseCaptor.capture());
        verify(verifyCredentialsResponseObserver).onCompleted();
        verify(verifyCredentialsResponseObserver, never()).onError(any());

        VerifyCredentialsResponse response = verifyCredentialsResponseCaptor.getValue();
        assertThat(response.getValid()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Invalid credentials");
    }

    // ==================== ListUsers Tests ====================

    @Test
    void listUsers_NoFilters() {
        // Arrange
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();

        User user2 = User.builder()
                .id("user2")
                .name("User Two")
                .email("user2@example.com")
                .password("password")
                .role(UserRole.RECIPIENT)
                .isActive(true)
                .build();
        user2.setCreatedAt(LocalDateTime.now());
        user2.setUpdatedAt(LocalDateTime.now());

        List<User> users = Arrays.asList(testUser, user2);
        when(userService.getAllUserEntities()).thenReturn(users);

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(userService).getAllUserEntities();
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());
        verify(listUsersResponseObserver).onCompleted();

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getUsersList()).hasSize(2);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
    }

    @Test
    void listUsers_WithRoleFilter() {
        // Arrange
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setRole(com.greengrub.proto.users.UserRole.DONOR)
                .setPage(0)
                .setPageSize(10)
                .build();

        List<User> donors = List.of(testUser);
        when(userService.getUserEntitiesByRole(UserRole.DONOR)).thenReturn(donors);

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(userService).getUserEntitiesByRole(UserRole.DONOR);
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getUsersList()).hasSize(1);
        assertThat(response.getUsers(0).getRole()).isEqualTo(com.greengrub.proto.users.UserRole.DONOR);
    }

    @Test
    void listUsers_WithActiveFilter() {
        // Arrange
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setIsActive(true)
                .setPage(0)
                .setPageSize(10)
                .build();

        List<User> activeUsers = List.of(testUser);
        when(userService.getUsersByActive(true)).thenReturn(activeUsers);

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(userService).getUsersByActive(true);
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getUsersList()).allMatch(user -> user.getIsActive());
    }

    @Test
    void listUsers_WithRoleAndActiveFilters() {
        // Arrange
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setRole(com.greengrub.proto.users.UserRole.DONOR)
                .setIsActive(true)
                .setPage(0)
                .setPageSize(10)
                .build();

        List<User> filteredUsers = List.of(testUser);
        when(userService.getUsersByRoleAndActive(UserRole.DONOR, true)).thenReturn(filteredUsers);

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(userService).getUsersByRoleAndActive(UserRole.DONOR, true);
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getUsersList()).hasSize(1);
    }

    @Test
    void listUsers_WithPagination() {
        // Arrange
        List<User> manyUsers = Arrays.asList(
                createUser("1", "user1@example.com"),
                createUser("2", "user2@example.com"),
                createUser("3", "user3@example.com"),
                createUser("4", "user4@example.com"),
                createUser("5", "user5@example.com")
        );

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(1)
                .setPageSize(2)
                .build();

        when(userService.getAllUserEntities()).thenReturn(manyUsers);

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getUsersList()).hasSize(2); // Page size 2
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getUsers(0).getUserId()).isEqualTo("3"); // Third user (page 1, index 2)
    }

    @Test
    void listUsers_EmptyResult() {
        // Arrange
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();

        when(userService.getAllUserEntities()).thenReturn(List.of());

        // Act
        grpcService.listUsers(request, listUsersResponseObserver);

        // Assert
        verify(listUsersResponseObserver).onNext(listUsersResponseCaptor.capture());

        ListUsersResponse response = listUsersResponseCaptor.getValue();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getUsersList()).isEmpty();
    }

    // ==================== Helper Methods ====================

    private User createUser(String id, String email) {
        User user = User.builder()
                .id(id)
                .name("User " + id)
                .email(email)
                .password("password")
                .role(UserRole.DONOR)
                .isActive(true)
                .build();
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
