package com.greengrub.usermanagement.controller;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication endpoints
 * Handles login, signup, and token validation
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Signup, login, and token introspection. Login/signup mint JWTs that the API Gateway will validate on subsequent calls.")
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "Register a new user (Signup)",
        description = "Create a new user account and receive a JWT token immediately. No authentication required."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error - Invalid input data"),
        @ApiResponse(responseCode = "409", description = "User already exists with this email")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "User registration details. Choose role: DONOR (donates food), RECIPIENT (receives food), or ADMIN (administrative access)",
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "Donor Signup Example",
                    description = "Register as a food donor",
                    value = """
                        {
                          "name": "John Doe",
                          "email": "john.doe@example.com",
                          "password": "securePassword123",
                          "phoneNumber": "1234567890",
                          "address": "123 Main Street, City",
                          "role": "DONOR"
                        }
                        """
                ),
                @ExampleObject(
                    name = "Recipient Signup Example",
                    description = "Register as a food recipient",
                    value = """
                        {
                          "name": "Jane Smith",
                          "email": "jane.smith@example.com",
                          "password": "securePassword456",
                          "phoneNumber": "9876543210",
                          "address": "456 Oak Avenue, City",
                          "role": "RECIPIENT"
                        }
                        """
                )
            }
        )
    )
    @PostMapping("/signup")
    public ResponseEntity<com.greengrub.usermanagement.dto.ApiResponse> signup(@Valid @RequestBody UserCreateRequest request) {
        log.info("REST: Signup request for email: {}", request.getEmail());
        AuthResponse authResponse = authService.signup(request);
        log.info("REST: Signup successful for email: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(com.greengrub.usermanagement.dto.ApiResponse.success("User registered successfully", authResponse));
    }

    @Operation(
        summary = "Login (Get JWT Token)",
        description = "Authenticate with email and password to receive a JWT token. Use this token for all protected endpoints."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful - Copy the token from response"),
        @ApiResponse(responseCode = "400", description = "Invalid credentials or account deactivated"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Login credentials (email and password)",
        content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(
                name = "Login Example",
                value = """
                    {
                      "email": "john.doe@example.com",
                      "password": "securePassword123"
                    }
                    """
            )
        )
    )
    @PostMapping("/login")
    public ResponseEntity<com.greengrub.usermanagement.dto.ApiResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("REST: Login request for email: {}", request.getEmail());
        AuthResponse authResponse = authService.login(request);
        log.info("REST: Login successful for email: {}", request.getEmail());
        return ResponseEntity.ok(
                com.greengrub.usermanagement.dto.ApiResponse.success("Login successful", authResponse)
        );
    }

    @Operation(
        summary = "Validate JWT Token",
        description = "Decodes a JWT minted by /signup or /login and returns the user. Useful for the API Gateway and for debugging."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "400", description = "Invalid authorization header format"),
        @ApiResponse(responseCode = "401", description = "Token expired or invalid")
    })
    @GetMapping("/validate")
    public ResponseEntity<com.greengrub.usermanagement.dto.ApiResponse> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("REST: Token validation request");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(com.greengrub.usermanagement.dto.ApiResponse.builder()
                            .success(false)
                            .message("Invalid authorization header format")
                            .build());
        }

        String token = authHeader.substring(7);
        UserResponse userResponse = authService.validateToken(token);

        log.info("REST: Token validation successful for user: {}", userResponse.getEmail());
        return ResponseEntity.ok(
                com.greengrub.usermanagement.dto.ApiResponse.success("Token is valid", userResponse)
        );
    }

    @Operation(
        summary = "Get Current User Info",
        description = "Returns the identity injected by the API Gateway via X-User-Id / X-User-Email / X-User-Role headers."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved"),
        @ApiResponse(responseCode = "403", description = "No identity headers present (request did not come through the gateway)")
    })
    @GetMapping("/me")
    public ResponseEntity<com.greengrub.usermanagement.dto.ApiResponse> getCurrentUser(
            @RequestAttribute(value = "userEmail", required = false) String email,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userRole", required = false) String role) {
        log.info("REST: Get current user request");

        if (email == null) {
            return ResponseEntity.status(403)
                    .body(com.greengrub.usermanagement.dto.ApiResponse.builder()
                            .success(false)
                            .message("Authentication required")
                            .build());
        }

        log.info("REST: Get current user request for: {}", email);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("email", email);
        userInfo.put("userId", userId);
        userInfo.put("role", role);

        return ResponseEntity.ok(
                com.greengrub.usermanagement.dto.ApiResponse.success("Current user retrieved", userInfo)
        );
    }
}
