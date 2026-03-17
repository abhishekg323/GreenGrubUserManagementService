package com.greengrub.usermanagement.dto;

import com.greengrub.usermanagement.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user response
 * Excludes sensitive information like password
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private String id;
    private String name;
    private String email;
    private String phoneNumber;
    private String address;
    private Boolean isActive;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
