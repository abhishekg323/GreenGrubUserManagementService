package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import com.greengrub.usermanagement.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for authentication operations (login, signup)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /**
     * Register a new user (signup)
     */
    @Transactional
    public AuthResponse signup(UserCreateRequest request) {
        log.info("Signup attempt for email: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Signup failed - email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException(request.getEmail());
        }

        // Create user entity
        User user = userMapper.toEntity(request);

        // Hash password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(hashedPassword);
        log.debug("Password hashed successfully for user: {}", request.getEmail());

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        // Generate JWT token
        String token = jwtUtil.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );

        UserResponse userResponse = userMapper.toResponse(savedUser);
        return AuthResponse.from(token, userResponse);
    }

    /**
     * Authenticate user (login)
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.getEmail());
                    return new UserNotFoundException("Invalid email or password");
                });

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("Login failed - user is inactive: {}", request.getEmail());
            throw new InvalidPasswordException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - invalid password for: {}", request.getEmail());
            throw new InvalidPasswordException("Invalid email or password");
        }

        log.info("Login successful for user: {}", request.getEmail());

        // Generate JWT token
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        UserResponse userResponse = userMapper.toResponse(user);
        return AuthResponse.from(token, userResponse);
    }

    /**
     * Validate JWT token and return user info
     */
    @Transactional(readOnly = true)
    public UserResponse validateToken(String token) {
        String email = jwtUtil.extractUsername(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for token"));

        if (!user.getIsActive()) {
            throw new InvalidPasswordException("Account is deactivated");
        }

        return userMapper.toResponse(user);
    }
}
