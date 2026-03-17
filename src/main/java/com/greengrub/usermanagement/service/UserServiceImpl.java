package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of UserService
 * Handles all business logic for user management including password hashing
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("User already exists with email: {}", request.getEmail());
            throw new UserAlreadyExistsException(request.getEmail());
        }

        // Map DTO to entity
        User user = userMapper.toEntity(request);

        // Hash password using BCrypt
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        log.debug("Password hashed successfully for user: {}", request.getEmail());

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse getUserById(String userId) {
        log.info("Fetching user by id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        log.info("Fetching user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("email", email));

        return userMapper.toResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");

        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveUsers() {
        log.info("Fetching all active users");

        return userRepository.findByIsActiveTrue().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getUsersByRole(UserRole role) {
        log.info("Fetching users by role: {}", role);

        return userRepository.findByRole(role).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Check if email is being changed and if it's already taken
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                log.warn("Email already exists: {}", request.getEmail());
                throw new UserAlreadyExistsException(request.getEmail());
            }
        }

        // Update user fields
        userMapper.updateEntity(user, request);

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with id: {}", updatedUser.getId());

        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void updatePassword(String userId, String oldPassword, String newPassword) {
        log.info("Updating password for user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Invalid old password provided for user: {}", userId);
            throw new InvalidPasswordException("Old password is incorrect");
        }

        // Validate new password (basic validation, add more as needed)
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }

        // Hash and save new password
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);
        userRepository.save(user);

        log.info("Password updated successfully for user: {}", userId);
    }

    @Override
    @Transactional
    public void resetPassword(String userId, String newPassword) {
        log.info("Resetting password for user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }

        // Hash and save new password
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", userId);
    }

    @Override
    public boolean verifyPassword(String userId, String password) {
        log.debug("Verifying password for user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        boolean matches = passwordEncoder.matches(password, user.getPassword());
        log.debug("Password verification result for user {}: {}", userId, matches);

        return matches;
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        log.info("Soft deleting user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.deactivate();
        userRepository.save(user);

        log.info("User soft deleted successfully with id: {}", userId);
    }

    @Override
    @Transactional
    public void permanentlyDeleteUser(String userId) {
        log.info("Permanently deleting user with id: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        userRepository.deleteById(userId);
        log.info("User permanently deleted with id: {}", userId);
    }

    @Override
    @Transactional
    public UserResponse activateUser(String userId) {
        log.info("Activating user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.activate();
        User activatedUser = userRepository.save(user);

        log.info("User activated successfully with id: {}", userId);
        return userMapper.toResponse(activatedUser);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(String userId) {
        log.info("Deactivating user with id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.deactivate();
        User deactivatedUser = userRepository.save(user);

        log.info("User deactivated successfully with id: {}", userId);
        return userMapper.toResponse(deactivatedUser);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public List<UserResponse> searchUsersByName(String name) {
        log.info("Searching users by name: {}", name);

        return userRepository.findByNameContainingIgnoreCase(name).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveDonors() {
        log.info("Fetching all active donors");

        return userRepository.findAllActiveDonors(UserRole.DONOR).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveRecipients() {
        log.info("Fetching all active recipients");

        return userRepository.findAllActiveRecipients(UserRole.RECIPIENT).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long countUsersByRole(UserRole role) {
        return userRepository.countByRole(role);
    }

    @Override
    public long countActiveUsers() {
        return userRepository.countActiveUsers();
    }

    // ============= gRPC Helper Methods =============

    @Override
    public User createUser(User user) {
        log.info("Creating user via gRPC: {}", user.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("User with email " + user.getEmail() + " already exists");
        }

        return userRepository.save(user);
    }

    @Override
    public User updateUser(User user) {
        log.info("Updating user via gRPC: {}", user.getId());

        if (!userRepository.existsById(user.getId())) {
            throw new RuntimeException("User not found with ID: " + user.getId());
        }

        return userRepository.save(user);
    }

    @Override
    public User getUserEntityById(String userId) {
        log.debug("Fetching user entity by ID: {}", userId);

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    @Override
    public User getUserEntityByEmail(String email) {
        log.debug("Fetching user entity by email: {}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    @Override
    public List<User> getUsersByRoleAndActive(UserRole role, boolean isActive) {
        log.debug("Fetching users by role: {} and active: {}", role, isActive);

        return userRepository.findByRoleAndIsActive(role, isActive);
    }

    @Override
    public List<User> getUsersByActive(boolean isActive) {
        log.debug("Fetching users by active status: {}", isActive);

        return userRepository.findByIsActive(isActive);
    }

    @Override
    public List<User> getAllUserEntities() {
        log.debug("Fetching all user entities");

        return userRepository.findAll();
    }

    @Override
    public List<User> getUserEntitiesByRole(UserRole role) {
        log.debug("Fetching user entities by role: {}", role);

        return userRepository.findByRole(role);
    }
}
