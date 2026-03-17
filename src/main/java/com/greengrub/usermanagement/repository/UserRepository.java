package com.greengrub.usermanagement.repository;

import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity
 * Provides CRUD operations and custom queries
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by role
     */
    List<User> findByRole(UserRole role);

    /**
     * Find all active users
     */
    List<User> findByIsActiveTrue();

    /**
     * Find all inactive users
     */
    List<User> findByIsActiveFalse();

    /**
     * Find users by role and active status
     */
    List<User> findByRoleAndIsActive(UserRole role, Boolean isActive);

    /**
     * Find users by name containing (case-insensitive search)
     */
    List<User> findByNameContainingIgnoreCase(String name);

    /**
     * Find user by phone number
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Count users by role
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") UserRole role);

    /**
     * Count active users
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    /**
     * Find all donors (users with DONOR role)
     */
    @Query("SELECT u FROM User u WHERE u.role = :donorRole AND u.isActive = true")
    List<User> findAllActiveDonors(@Param("donorRole") UserRole donorRole);

    /**
     * Find all recipients (users with RECIPIENT role)
     */
    @Query("SELECT u FROM User u WHERE u.role = :recipientRole AND u.isActive = true")
    List<User> findAllActiveRecipients(@Param("recipientRole") UserRole recipientRole);

    /**
     * Find users by active status (for gRPC)
     */
    List<User> findByIsActive(Boolean isActive);
}
