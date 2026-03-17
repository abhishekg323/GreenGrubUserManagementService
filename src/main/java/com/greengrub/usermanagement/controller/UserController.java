package com.greengrub.usermanagement.controller;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for User management operations
 * Provides CRUD endpoints for user management
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "CRUD operations for managing users. Most endpoints require authentication (click Authorize button first!).")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    /**
     * Create a new user
     * POST /api/v1/users
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("REST: Creating new user with email: {}", request.getEmail());

        UserResponse user = userService.createUser(request);

        log.info("REST: User created successfully with id: {}", user.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    /**
     * Get user by ID
     * GET /api/v1/users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String userId) {
        log.info("REST: Fetching user by id: {}", userId);

        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Get user by email
     * GET /api/v1/users/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        log.info("REST: Fetching user by email: {}", email);

        UserResponse user = userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    /**
     * Get all users
     * GET /api/v1/users
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UserRole role) {

        log.info("REST: Fetching users - active: {}, role: {}", active, role);

        List<UserResponse> users;

        if (role != null) {
            users = userService.getUsersByRole(role);
        } else if (active != null && active) {
            users = userService.getActiveUsers();
        } else {
            users = userService.getAllUsers();
        }

        return ResponseEntity.ok(users);
    }

    /**
     * Search users by name
     * GET /api/v1/users/search?name={name}
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam String name) {
        log.info("REST: Searching users by name: {}", name);

        List<UserResponse> users = userService.searchUsersByName(name);
        return ResponseEntity.ok(users);
    }

    /**
     * Get active donors
     * GET /api/v1/users/donors
     */
    @GetMapping("/donors")
    public ResponseEntity<List<UserResponse>> getActiveDonors() {
        log.info("REST: Fetching active donors");

        List<UserResponse> donors = userService.getActiveDonors();
        return ResponseEntity.ok(donors);
    }

    /**
     * Get active recipients
     * GET /api/v1/users/recipients
     */
    @GetMapping("/recipients")
    public ResponseEntity<List<UserResponse>> getActiveRecipients() {
        log.info("REST: Fetching active recipients");

        List<UserResponse> recipients = userService.getActiveRecipients();
        return ResponseEntity.ok(recipients);
    }

    /**
     * Update user
     * PUT /api/v1/users/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserUpdateRequest request) {

        log.info("REST: Updating user with id: {}", userId);

        UserResponse user = userService.updateUser(userId, request);

        log.info("REST: User updated successfully with id: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    /**
     * Update user password
     * PUT /api/v1/users/{userId}/password
     */
    @PutMapping("/{userId}/password")
    public ResponseEntity<ApiResponse> updatePassword(
            @PathVariable String userId,
            @Valid @RequestBody PasswordUpdateRequest request) {

        log.info("REST: Updating password for user: {}", userId);

        userService.updatePassword(userId, request.getOldPassword(), request.getNewPassword());

        log.info("REST: Password updated successfully for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }

    /**
     * Reset user password (admin function)
     * PUT /api/v1/users/{userId}/reset-password
     */
    @PutMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @PathVariable String userId,
            @Valid @RequestBody PasswordResetRequest request) {

        log.info("REST: Resetting password for user: {}", userId);

        userService.resetPassword(userId, request.getNewPassword());

        log.info("REST: Password reset successfully for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    /**
     * Activate user
     * PUT /api/v1/users/{userId}/activate
     */
    @PutMapping("/{userId}/activate")
    public ResponseEntity<ApiResponse> activateUser(@PathVariable String userId) {
        log.info("REST: Activating user: {}", userId);

        UserResponse user = userService.activateUser(userId);

        log.info("REST: User activated successfully: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User activated successfully", user));
    }

    /**
     * Deactivate user
     * PUT /api/v1/users/{userId}/deactivate
     */
    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse> deactivateUser(@PathVariable String userId) {
        log.info("REST: Deactivating user: {}", userId);

        UserResponse user = userService.deactivateUser(userId);

        log.info("REST: User deactivated successfully: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", user));
    }

    /**
     * Soft delete user
     * DELETE /api/v1/users/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable String userId) {
        log.info("REST: Soft deleting user: {}", userId);

        userService.deleteUser(userId);

        log.info("REST: User soft deleted successfully: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }

    /**
     * Permanently delete user
     * DELETE /api/v1/users/{userId}/permanent
     */
    @DeleteMapping("/{userId}/permanent")
    public ResponseEntity<ApiResponse> permanentlyDeleteUser(@PathVariable String userId) {
        log.info("REST: Permanently deleting user: {}", userId);

        userService.permanentlyDeleteUser(userId);

        log.info("REST: User permanently deleted: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User permanently deleted"));
    }

    /**
     * Check if email exists
     * GET /api/v1/users/check-email?email={email}
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse> checkEmailExists(@RequestParam String email) {
        log.info("REST: Checking if email exists: {}", email);

        boolean exists = userService.existsByEmail(email);

        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message(exists ? "Email already exists" : "Email is available")
                        .data(exists)
                        .build()
        );
    }

    /**
     * Get user statistics
     * GET /api/v1/users/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getUserStats() {
        log.info("REST: Fetching user statistics");

        long totalUsers = userService.getAllUsers().size();
        long activeUsers = userService.countActiveUsers();
        long donors = userService.countUsersByRole(UserRole.DONOR);
        long recipients = userService.countUsersByRole(UserRole.RECIPIENT);

        var stats = new java.util.HashMap<String, Long>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("donors", donors);
        stats.put("recipients", recipients);

        return ResponseEntity.ok(ApiResponse.success("User statistics retrieved", stats));
    }
}
