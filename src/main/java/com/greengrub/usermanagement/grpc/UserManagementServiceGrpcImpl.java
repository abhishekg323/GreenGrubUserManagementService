package com.greengrub.usermanagement.grpc;

import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * gRPC Service Implementation for User Management Service
 * Provides inter-service communication via gRPC protocol
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserManagementServiceGrpcImpl extends UserManagementServiceGrpc.UserManagementServiceImplBase {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Get user by ID
     */
    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC GetUser called for userId: {}", request.getUserId());

            User user = userService.getUserEntityById(request.getUserId());
            UserResponse response = mapToUserResponse(user);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC GetUser completed successfully for userId: {}", request.getUserId());
        } catch (Exception e) {
            log.error("gRPC GetUser failed for userId: {}", request.getUserId(), e);
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("User not found: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Get user by email
     */
    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC GetUserByEmail called for email: {}", request.getEmail());

            User user = userService.getUserEntityByEmail(request.getEmail());
            UserResponse response = mapToUserResponse(user);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC GetUserByEmail completed successfully");
        } catch (Exception e) {
            log.error("gRPC GetUserByEmail failed for email: {}", request.getEmail(), e);
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("User not found: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Create a new user
     */
    @Override
    public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC CreateUser called for email: {}", request.getEmail());

            // Validate required fields
            if (request.getName().isBlank() || request.getEmail().isBlank() ||
                request.getPassword().isBlank()) {
                throw new IllegalArgumentException("Name, email, and password are required");
            }

            // Create user entity
            User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber().isBlank() ? null : request.getPhoneNumber())
                .address(request.getAddress().isBlank() ? null : request.getAddress())
                .role(mapToEntityRole(request.getRole()))
                .isActive(true)
                .build();

            User savedUser = userService.createUser(user);
            UserResponse response = mapToUserResponse(savedUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC CreateUser completed successfully for userId: {}", savedUser.getId());
        } catch (Exception e) {
            log.error("gRPC CreateUser failed", e);
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Failed to create user: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Update existing user
     */
    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC UpdateUser called for userId: {}", request.getUserId());

            User existingUser = userService.getUserEntityById(request.getUserId());

            // Update only provided fields
            if (request.hasName() && !request.getName().isBlank()) {
                existingUser.setName(request.getName());
            }
            if (request.hasEmail() && !request.getEmail().isBlank()) {
                existingUser.setEmail(request.getEmail());
            }
            if (request.hasPhoneNumber()) {
                existingUser.setPhoneNumber(request.getPhoneNumber().isBlank() ? null : request.getPhoneNumber());
            }
            if (request.hasAddress()) {
                existingUser.setAddress(request.getAddress().isBlank() ? null : request.getAddress());
            }
            if (request.hasRole()) {
                existingUser.setRole(mapToEntityRole(request.getRole()));
            }

            User updatedUser = userService.updateUser(existingUser);
            UserResponse response = mapToUserResponse(updatedUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC UpdateUser completed successfully for userId: {}", request.getUserId());
        } catch (Exception e) {
            log.error("gRPC UpdateUser failed for userId: {}", request.getUserId(), e);
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Failed to update user: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Delete user (soft delete)
     */
    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<DeleteUserResponse> responseObserver) {
        try {
            log.info("gRPC DeleteUser called for userId: {}", request.getUserId());

            userService.deleteUser(request.getUserId());

            DeleteUserResponse response = DeleteUserResponse.newBuilder()
                .setSuccess(true)
                .setMessage("User deleted successfully")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC DeleteUser completed successfully for userId: {}", request.getUserId());
        } catch (Exception e) {
            log.error("gRPC DeleteUser failed for userId: {}", request.getUserId(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to delete user: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Activate user
     */
    @Override
    public void activateUser(ActivateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC ActivateUser called for userId: {}", request.getUserId());

            User user = userService.getUserEntityById(request.getUserId());
            user.activate();
            User updatedUser = userService.updateUser(user);

            UserResponse response = mapToUserResponse(updatedUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC ActivateUser completed successfully for userId: {}", request.getUserId());
        } catch (Exception e) {
            log.error("gRPC ActivateUser failed for userId: {}", request.getUserId(), e);
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("Failed to activate user: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Deactivate user
     */
    @Override
    public void deactivateUser(DeactivateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.info("gRPC DeactivateUser called for userId: {}", request.getUserId());

            User user = userService.getUserEntityById(request.getUserId());
            user.deactivate();
            User updatedUser = userService.updateUser(user);

            UserResponse response = mapToUserResponse(updatedUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC DeactivateUser completed successfully for userId: {}", request.getUserId());
        } catch (Exception e) {
            log.error("gRPC DeactivateUser failed for userId: {}", request.getUserId(), e);
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("Failed to deactivate user: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Verify user credentials for authentication
     */
    @Override
    public void verifyCredentials(VerifyCredentialsRequest request, StreamObserver<VerifyCredentialsResponse> responseObserver) {
        try {
            log.info("gRPC VerifyCredentials called for email: {}", request.getEmail());

            User user = userService.getUserEntityByEmail(request.getEmail());

            boolean isValid = passwordEncoder.matches(request.getPassword(), user.getPassword());

            VerifyCredentialsResponse.Builder responseBuilder = VerifyCredentialsResponse.newBuilder()
                .setValid(isValid);

            if (isValid) {
                responseBuilder.setUser(mapToUserResponse(user))
                    .setMessage("Credentials are valid");
            } else {
                responseBuilder.setMessage("Invalid password");
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            log.info("gRPC VerifyCredentials completed for email: {}, valid: {}", request.getEmail(), isValid);
        } catch (Exception e) {
            log.error("gRPC VerifyCredentials failed for email: {}", request.getEmail(), e);

            // Return invalid credentials (don't reveal if user exists)
            VerifyCredentialsResponse response = VerifyCredentialsResponse.newBuilder()
                .setValid(false)
                .setMessage("Invalid credentials")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * List users with optional filters and pagination
     */
    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        try {
            log.info("gRPC ListUsers called with filters - role: {}, active: {}, page: {}, size: {}",
                request.hasRole() ? request.getRole() : "ALL",
                request.hasIsActive() ? request.getIsActive() : "ALL",
                request.getPage(),
                request.getPageSize());

            // Get filtered users
            List<User> users;

            if (request.hasRole() && request.hasIsActive()) {
                users = userService.getUsersByRoleAndActive(
                    mapToEntityRole(request.getRole()),
                    request.getIsActive()
                );
            } else if (request.hasRole()) {
                users = userService.getUserEntitiesByRole(mapToEntityRole(request.getRole()));
            } else if (request.hasIsActive()) {
                users = userService.getUsersByActive(request.getIsActive());
            } else {
                users = userService.getAllUserEntities();
            }

            // Apply pagination (simple in-memory pagination)
            int page = request.getPage() > 0 ? request.getPage() : 0;
            int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 20;
            int totalCount = users.size();

            int fromIndex = page * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, totalCount);

            List<User> paginatedUsers = fromIndex < totalCount
                ? users.subList(fromIndex, toIndex)
                : List.of();

            // Build response
            ListUsersResponse.Builder responseBuilder = ListUsersResponse.newBuilder()
                .setTotalCount(totalCount)
                .setPage(page)
                .setPageSize(pageSize);

            paginatedUsers.stream()
                .map(this::mapToUserResponse)
                .forEach(responseBuilder::addUsers);

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            log.info("gRPC ListUsers completed successfully. Total: {}, Returned: {}",
                totalCount, paginatedUsers.size());
        } catch (Exception e) {
            log.error("gRPC ListUsers failed", e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to list users: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Map User entity to gRPC UserResponse
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.newBuilder()
            .setUserId(user.getId())
            .setName(user.getName())
            .setEmail(user.getEmail())
            .setPhoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
            .setAddress(user.getAddress() != null ? user.getAddress() : "")
            .setIsActive(user.getIsActive())
            .setRole(mapToProtoRole(user.getRole()))
            .setCreatedAt(user.getCreatedAt().format(ISO_FORMATTER))
            .setUpdatedAt(user.getUpdatedAt().format(ISO_FORMATTER))
            .build();
    }

    /**
     * Map proto UserRole to entity UserRole
     */
    private UserRole mapToEntityRole(com.greengrub.usermanagement.grpc.UserRole protoRole) {
        return switch (protoRole) {
            case DONOR -> UserRole.DONOR;
            case RECIPIENT -> UserRole.RECIPIENT;
            case ADMIN -> UserRole.ADMIN;
            default -> UserRole.DONOR; // Default to DONOR
        };
    }

    /**
     * Map entity UserRole to proto UserRole
     */
    private com.greengrub.usermanagement.grpc.UserRole mapToProtoRole(UserRole entityRole) {
        return switch (entityRole) {
            case DONOR -> com.greengrub.usermanagement.grpc.UserRole.DONOR;
            case RECIPIENT -> com.greengrub.usermanagement.grpc.UserRole.RECIPIENT;
            case ADMIN -> com.greengrub.usermanagement.grpc.UserRole.ADMIN;
        };
    }
}
