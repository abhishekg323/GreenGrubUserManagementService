package com.greengrub.usermanagement.service;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.exception.UserStorageException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import com.greengrub.usermanagement.security.JwtUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String RETRY_NAME = "userRetry";
    private static final String CB_NAME = "userBreaker";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse signup(UserCreateRequest request) {
        log.info("Signup attempt for email: {}", request.getEmail());

        if (existsByEmail(request.getEmail())) {
            log.warn("Signup failed - email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException(request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = saveUser(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        String token = jwtUtil.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );

        return AuthResponse.from(token, userMapper.toResponse(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        User user = findUserByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.getEmail());
                    return new UserNotFoundException("Invalid email or password");
                });

        if (!user.getIsActive()) {
            log.warn("Login failed - user is inactive: {}", request.getEmail());
            throw new InvalidPasswordException("Account is deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - invalid password for: {}", request.getEmail());
            throw new InvalidPasswordException("Invalid email or password");
        }

        log.info("Login successful for user: {}", request.getEmail());

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return AuthResponse.from(token, userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse validateToken(String token) {
        String email = jwtUtil.extractUsername(token);

        User user = findUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for token"));

        if (!user.getIsActive()) {
            throw new InvalidPasswordException("Account is deactivated");
        }

        return userMapper.toResponse(user);
    }

    // ============= Resilience-wrapped repository helpers =============

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected boolean existsByEmail(String email) {
        try {
            return userRepository.existsByEmail(email);
        } catch (Exception e) {
            log.error("Transient failure checking email existence {}: {}", email, e.getMessage());
            throw new UserStorageException("Failed to check email existence", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Transient failure saving user during auth: {}", e.getMessage());
            throw new UserStorageException("Failed to save user", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected java.util.Optional<User> findUserByEmail(String email) {
        try {
            return userRepository.findByEmail(email);
        } catch (Exception e) {
            log.error("Transient failure finding user by email {}: {}", email, e.getMessage());
            throw new UserStorageException("Failed to fetch user by email", e);
        }
    }
}
