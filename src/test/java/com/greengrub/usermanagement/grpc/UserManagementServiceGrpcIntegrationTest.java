package com.greengrub.usermanagement.grpc;

import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.repository.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for gRPC CustomerService
 * Tests the actual gRPC server with real database interactions
 *
 * Note: Requires gRPC server to be running on port 9091
 * Run the application first, then run these tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "grpc.server.port=9091",
        "server.port=8082",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "app.data.init.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Disabled("Integration test - requires running gRPC server. Enable manually for integration testing.")
class UserManagementServiceGrpcIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ManagedChannel channel;
    private UserManagementServiceGrpc.UserManagementServiceBlockingStub blockingStub;

    private User testUser;
    private static final String TEST_EMAIL = "grpc-test@example.com";
    private static final String TEST_PASSWORD = "TestPassword123";

    @BeforeAll
    void setupChannel() {
        // Create gRPC channel
        channel = ManagedChannelBuilder
                .forAddress("localhost", 9091)
                .usePlaintext()
                .build();

        blockingStub = UserManagementServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    void tearDownChannel() throws InterruptedException {
        if (channel != null) {
            channel.shutdown();
            channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void setUp() {
        // Clean database before each test
        userRepository.deleteAll();

        // Create test user
        testUser = User.builder()
                .name("gRPC Test User")
                .email(TEST_EMAIL)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .phoneNumber("1234567890")
                .address("123 gRPC St")
                .role(UserRole.DONOR)
                .isActive(true)
                .build();

        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        userRepository.deleteAll();
    }

    // ==================== GetUser Tests ====================

    @Test
    void getUser_Success() {
        // Arrange
        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .build();

        // Act
        UserResponse response = blockingStub.getUser(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getName()).isEqualTo("gRPC Test User");
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getRole()).isEqualTo(com.greengrub.usermanagement.grpc.UserRole.DONOR);
        assertThat(response.getCreatedAt()).isNotEmpty();
        assertThat(response.getUpdatedAt()).isNotEmpty();
    }

    @Test
    void getUser_NotFound() {
        // Arrange
        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId("non-existent-id")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.getUser(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    // ==================== GetUserByEmail Tests ====================

    @Test
    void getUserByEmail_Success() {
        // Arrange
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail(TEST_EMAIL)
                .build();

        // Act
        UserResponse response = blockingStub.getUserByEmail(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getName()).isEqualTo("gRPC Test User");
    }

    @Test
    void getUserByEmail_NotFound() {
        // Arrange
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail("nonexistent@example.com")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.getUserByEmail(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    // ==================== CreateUser Tests ====================

    @Test
    void createUser_Success() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("New gRPC User")
                .setEmail("newgrpc@example.com")
                .setPassword("NewPassword123")
                .setPhoneNumber("9876543210")
                .setAddress("456 New St")
                .setRole(com.greengrub.usermanagement.grpc.UserRole.RECIPIENT)
                .build();

        // Act
        UserResponse response = blockingStub.createUser(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isNotEmpty();
        assertThat(response.getName()).isEqualTo("New gRPC User");
        assertThat(response.getEmail()).isEqualTo("newgrpc@example.com");
        assertThat(response.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(response.getRole()).isEqualTo(com.greengrub.usermanagement.grpc.UserRole.RECIPIENT);
        assertThat(response.getIsActive()).isTrue();

        // Verify in database
        User savedUser = userRepository.findByEmail("newgrpc@example.com").orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("New gRPC User");
        // Verify password was hashed
        assertThat(savedUser.getPassword()).isNotEqualTo("NewPassword123");
        assertThat(passwordEncoder.matches("NewPassword123", savedUser.getPassword())).isTrue();
    }

    @Test
    void createUser_DuplicateEmail() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("Duplicate User")
                .setEmail(TEST_EMAIL) // Same as testUser
                .setPassword("Password123")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.createUser(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT")
                .hasMessageContaining("already exists");
    }

    @Test
    void createUser_MissingRequiredFields() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("") // Empty name
                .setEmail("test@example.com")
                .setPassword("password")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.createUser(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    // ==================== UpdateUser Tests ====================

    @Test
    void updateUser_PartialUpdate_Success() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .setName("Updated gRPC Name")
                .setPhoneNumber("9999999999")
                .build();

        // Act
        UserResponse response = blockingStub.updateUser(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Updated gRPC Name");
        assertThat(response.getPhoneNumber()).isEqualTo("9999999999");
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL); // Unchanged

        // Verify in database
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getName()).isEqualTo("Updated gRPC Name");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("9999999999");
    }

    @Test
    void updateUser_ChangeRole() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .setRole(com.greengrub.usermanagement.grpc.UserRole.ADMIN)
                .build();

        // Act
        UserResponse response = blockingStub.updateUser(request);

        // Assert
        assertThat(response.getRole()).isEqualTo(com.greengrub.usermanagement.grpc.UserRole.ADMIN);

        // Verify in database
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updateUser_NotFound() {
        // Arrange
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("non-existent-id")
                .setName("New Name")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.updateUser(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    // ==================== DeleteUser Tests ====================

    @Test
    void deleteUser_Success() {
        // Arrange
        DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .build();

        // Act
        DeleteUserResponse response = blockingStub.deleteUser(request);

        // Assert
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successfully");

        // Verify user is deactivated (soft delete)
        User deletedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(deletedUser.getIsActive()).isFalse();
    }

    @Test
    void deleteUser_NotFound() {
        // Arrange
        DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId("non-existent-id")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.deleteUser(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INTERNAL");
    }

    // ==================== ActivateUser Tests ====================

    @Test
    void activateUser_Success() {
        // Arrange - First deactivate the user
        testUser.deactivate();
        userRepository.save(testUser);

        ActivateUserRequest request = ActivateUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .build();

        // Act
        UserResponse response = blockingStub.activateUser(request);

        // Assert
        assertThat(response.getIsActive()).isTrue();

        // Verify in database
        User activatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(activatedUser.getIsActive()).isTrue();
    }

    // ==================== DeactivateUser Tests ====================

    @Test
    void deactivateUser_Success() {
        // Arrange
        DeactivateUserRequest request = DeactivateUserRequest.newBuilder()
                .setUserId(testUser.getId())
                .build();

        // Act
        UserResponse response = blockingStub.deactivateUser(request);

        // Assert
        assertThat(response.getIsActive()).isFalse();

        // Verify in database
        User deactivatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(deactivatedUser.getIsActive()).isFalse();
    }

    // ==================== VerifyCredentials Tests ====================

    @Test
    void verifyCredentials_ValidCredentials() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail(TEST_EMAIL)
                .setPassword(TEST_PASSWORD)
                .build();

        // Act
        VerifyCredentialsResponse response = blockingStub.verifyCredentials(request);

        // Assert
        assertThat(response.getValid()).isTrue();
        assertThat(response.hasUser()).isTrue();
        assertThat(response.getUser().getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getMessage()).contains("valid");
    }

    @Test
    void verifyCredentials_InvalidPassword() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail(TEST_EMAIL)
                .setPassword("WrongPassword")
                .build();

        // Act
        VerifyCredentialsResponse response = blockingStub.verifyCredentials(request);

        // Assert
        assertThat(response.getValid()).isFalse();
        assertThat(response.getMessage()).contains("Invalid password");
    }

    @Test
    void verifyCredentials_UserNotFound_DoesNotReveal() {
        // Arrange
        VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
                .setEmail("nonexistent@example.com")
                .setPassword("somePassword")
                .build();

        // Act
        VerifyCredentialsResponse response = blockingStub.verifyCredentials(request);

        // Assert - Should not throw exception, should return invalid
        assertThat(response.getValid()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Invalid credentials");
        assertThat(response.hasUser()).isFalse();
    }

    // ==================== ListUsers Tests ====================

    @Test
    void listUsers_NoFilters_Success() {
        // Arrange - Create additional users
        User user2 = createAndSaveUser("user2@example.com", UserRole.RECIPIENT, true);
        User user3 = createAndSaveUser("user3@example.com", UserRole.ADMIN, true);

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(3); // testUser + user2 + user3
        assertThat(response.getUsersList()).hasSize(3);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
    }

    @Test
    void listUsers_FilterByRole() {
        // Arrange
        createAndSaveUser("donor1@example.com", UserRole.DONOR, true);
        createAndSaveUser("donor2@example.com", UserRole.DONOR, true);
        createAndSaveUser("recipient1@example.com", UserRole.RECIPIENT, true);

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setRole(com.greengrub.usermanagement.grpc.UserRole.DONOR)
                .setPage(0)
                .setPageSize(10)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(3); // testUser + donor1 + donor2
        assertThat(response.getUsersList())
                .allMatch(user -> user.getRole() == com.greengrub.usermanagement.grpc.UserRole.DONOR);
    }

    @Test
    void listUsers_FilterByActive() {
        // Arrange
        createAndSaveUser("active@example.com", UserRole.DONOR, true);
        createAndSaveUser("inactive@example.com", UserRole.DONOR, false);

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setIsActive(true)
                .setPage(0)
                .setPageSize(10)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(2); // testUser + active
        assertThat(response.getUsersList()).allMatch(UserResponse::getIsActive);
    }

    @Test
    void listUsers_FilterByRoleAndActive() {
        // Arrange
        createAndSaveUser("active-donor@example.com", UserRole.DONOR, true);
        createAndSaveUser("inactive-donor@example.com", UserRole.DONOR, false);
        createAndSaveUser("active-recipient@example.com", UserRole.RECIPIENT, true);

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setRole(com.greengrub.usermanagement.grpc.UserRole.DONOR)
                .setIsActive(true)
                .setPage(0)
                .setPageSize(10)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(2); // testUser + active-donor
        assertThat(response.getUsersList())
                .allMatch(user -> user.getRole() == com.greengrub.usermanagement.grpc.UserRole.DONOR && user.getIsActive());
    }

    @Test
    void listUsers_WithPagination() {
        // Arrange - Create 5 users total
        createAndSaveUser("user1@example.com", UserRole.DONOR, true);
        createAndSaveUser("user2@example.com", UserRole.DONOR, true);
        createAndSaveUser("user3@example.com", UserRole.DONOR, true);
        createAndSaveUser("user4@example.com", UserRole.DONOR, true);

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(1)
                .setPageSize(2)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getUsersList()).hasSize(2); // Page size 2
        assertThat(response.getPage()).isEqualTo(1);
    }

    @Test
    void listUsers_EmptyResult() {
        // Arrange - Delete all users
        userRepository.deleteAll();

        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();

        // Act
        ListUsersResponse response = blockingStub.listUsers(request);

        // Assert
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getUsersList()).isEmpty();
    }

    // ==================== End-to-End Scenario Tests ====================

    @Test
    void endToEndScenario_CreateVerifyUpdateDelete() {
        // 1. Create user
        CreateUserRequest createRequest = CreateUserRequest.newBuilder()
                .setName("E2E Test User")
                .setEmail("e2e@example.com")
                .setPassword("E2EPassword123")
                .setRole(com.greengrub.usermanagement.grpc.UserRole.DONOR)
                .build();

        UserResponse createdUser = blockingStub.createUser(createRequest);
        String userId = createdUser.getUserId();
        assertThat(userId).isNotEmpty();

        // 2. Verify credentials
        VerifyCredentialsRequest verifyRequest = VerifyCredentialsRequest.newBuilder()
                .setEmail("e2e@example.com")
                .setPassword("E2EPassword123")
                .build();

        VerifyCredentialsResponse verifyResponse = blockingStub.verifyCredentials(verifyRequest);
        assertThat(verifyResponse.getValid()).isTrue();

        // 3. Update user
        UpdateUserRequest updateRequest = UpdateUserRequest.newBuilder()
                .setUserId(userId)
                .setName("E2E Updated Name")
                .setRole(com.greengrub.usermanagement.grpc.UserRole.ADMIN)
                .build();

        UserResponse updatedUser = blockingStub.updateUser(updateRequest);
        assertThat(updatedUser.getName()).isEqualTo("E2E Updated Name");
        assertThat(updatedUser.getRole()).isEqualTo(com.greengrub.usermanagement.grpc.UserRole.ADMIN);

        // 4. Deactivate user
        DeactivateUserRequest deactivateRequest = DeactivateUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        UserResponse deactivatedUser = blockingStub.deactivateUser(deactivateRequest);
        assertThat(deactivatedUser.getIsActive()).isFalse();

        // 5. Activate user
        ActivateUserRequest activateRequest = ActivateUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        UserResponse activatedUser = blockingStub.activateUser(activateRequest);
        assertThat(activatedUser.getIsActive()).isTrue();

        // 6. Delete user
        DeleteUserRequest deleteRequest = DeleteUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        DeleteUserResponse deleteResponse = blockingStub.deleteUser(deleteRequest);
        assertThat(deleteResponse.getSuccess()).isTrue();

        // Verify soft delete
        User deletedUser = userRepository.findById(userId).orElseThrow();
        assertThat(deletedUser.getIsActive()).isFalse();
    }

    // ==================== Helper Methods ====================

    private User createAndSaveUser(String email, UserRole role, boolean isActive) {
        User user = User.builder()
                .name("Test User " + email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .isActive(isActive)
                .build();
        return userRepository.save(user);
    }
}
