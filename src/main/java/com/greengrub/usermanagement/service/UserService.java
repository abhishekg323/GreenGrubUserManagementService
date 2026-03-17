package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;

import java.util.List;

/**
 * Service interface for User operations
 * Defines the contract for user management business logic
 */
public interface UserService {

    /**
     * Create a new user
     * Password will be automatically hashed before storage
     */
    UserResponse createUser(UserCreateRequest request);

    /**
     * Get user by ID
     */
    UserResponse getUserById(String userId);

    /**
     * Get user by email
     */
    UserResponse getUserByEmail(String email);

    /**
     * Get all users
     */
    List<UserResponse> getAllUsers();

    /**
     * Get all active users
     */
    List<UserResponse> getActiveUsers();

    /**
     * Get users by role
     */
    List<UserResponse> getUsersByRole(UserRole role);

    /**
     * Update user
     */
    UserResponse updateUser(String userId, UserUpdateRequest request);

    /**
     * Update user password
     * Old password will be verified before updating
     * New password will be hashed before storage
     */
    void updatePassword(String userId, String oldPassword, String newPassword);

    /**
     * Reset user password (admin function)
     * New password will be hashed before storage
     */
    void resetPassword(String userId, String newPassword);

    /**
     * Verify user password
     * Compares plain text password with hashed password in database
     */
    boolean verifyPassword(String userId, String password);

    /**
     * Delete user (soft delete - deactivate)
     */
    void deleteUser(String userId);

    /**
     * Permanently delete user (hard delete)
     */
    void permanentlyDeleteUser(String userId);

    /**
     * Activate user account
     */
    UserResponse activateUser(String userId);

    /**
     * Deactivate user account
     */
    UserResponse deactivateUser(String userId);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Search users by name
     */
    List<UserResponse> searchUsersByName(String name);

    /**
     * Get all active donors
     */
    List<UserResponse> getActiveDonors();

    /**
     * Get all active recipients
     */
    List<UserResponse> getActiveRecipients();

    /**
     * Get user count by role
     */
    long countUsersByRole(UserRole role);

    /**
     * Get active user count
     */
    long countActiveUsers();

    // ============= Additional methods for gRPC =============

    /**
     * Create user from entity (for gRPC)
     */
    User createUser(User user);

    /**
     * Update user from entity (for gRPC)
     */
    User updateUser(User user);

    /**
     * Get user entity by ID (for gRPC)
     */
    User getUserEntityById(String userId);

    /**
     * Get user entity by email (for gRPC)
     */
    User getUserEntityByEmail(String email);

    /**
     * Get users by role and active status (for gRPC filtering)
     */
    List<User> getUsersByRoleAndActive(UserRole role, boolean isActive);

    /**
     * Get users by active status (for gRPC filtering)
     */
    List<User> getUsersByActive(boolean isActive);

    /**
     * Get all user entities (for gRPC)
     */
    List<User> getAllUserEntities();

    /**
     * Get user entities by role (for gRPC)
     */
    List<User> getUserEntitiesByRole(UserRole role);
}
