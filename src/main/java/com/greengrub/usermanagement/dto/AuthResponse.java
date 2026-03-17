package com.greengrub.usermanagement.dto;

import com.greengrub.usermanagement.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for successful authentication
 * Contains JWT token and user information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String tokenType;
    private String userId;
    private String email;
    private String name;
    private UserRole role;
    private boolean isActive;

    public static AuthResponse from(String token, UserResponse user) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .build();
    }
}
