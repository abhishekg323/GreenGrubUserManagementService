package com.greengrub.usermanagement.controller;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * REST Controller for User management operations
 * Provides CRUD endpoints for user management
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "CRUD operations for managing users. Authentication is enforced upstream by the API Gateway.")
public class UserController {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_DONATIONS_PAGE_SIZE = 100;

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
     * Upload a profile image for the user. Bytes are forwarded to image-service
     * over gRPC; on success the returned image id is persisted on the user row
     * and the response includes the resolved imageUrl.
     *
     * <p>Reject early — content-type and size are validated before we pay the
     * gRPC round-trip on garbage input.
     */
    @PostMapping(value = "/{userId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadProfileImage(@PathVariable String userId,
                                                          @RequestParam("file") MultipartFile file) {
        log.info("REST: Uploading profile image for user: {} ({} bytes, contentType={})",
                userId, file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds maximum allowed size of 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType + " (allowed: image/jpeg, image/png, image/webp)");
        }

        UserResponse user = userService.uploadProfileImage(userId, file);
        return ResponseEntity.ok(ApiResponse.success("Profile image uploaded", user));
    }

    /**
     * Clear the user's profile image pointer. Image-service still owns the bytes;
     * this endpoint only detaches them from the user.
     */
    @DeleteMapping("/{userId}/image")
    public ResponseEntity<ApiResponse> deleteProfileImage(@PathVariable String userId) {
        log.info("REST: Clearing profile image for user: {}", userId);
        UserResponse user = userService.deleteProfileImage(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile image cleared", user));
    }

    /**
     * List donations created by this user. The data lives in donation-service —
     * we forward the call over gRPC and map the proto response to a JSON-friendly
     * shape. Pagination defaults: page=0, pageSize=10. pageSize is capped at 100
     * to prevent runaway list pulls.
     */
    @GetMapping("/{userId}/donations")
    public ResponseEntity<DonationListView> getDonationsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        log.info("REST: Fetching donations for user: {} (page={}, pageSize={})", userId, page, pageSize);

        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_DONATIONS_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_DONATIONS_PAGE_SIZE);
        }

        DonationListView donations = userService.getDonationsByUserId(userId, page, pageSize);
        return ResponseEntity.ok(donations);
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
