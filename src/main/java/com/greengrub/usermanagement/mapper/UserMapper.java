package com.greengrub.usermanagement.mapper;

import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between User entity and DTOs
 * Follows the mapper pattern for clean separation of concerns
 */
@Component
public class UserMapper {

    /**
     * Convert UserCreateRequest to User entity
     */
    public User toEntity(UserCreateRequest request) {
        return User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(request.getPassword()) // Should be encoded before saving
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .role(request.getRole())
                .isActive(true)
                .build();
    }

    /**
     * Convert User entity to UserResponse DTO. The imageUrl field is left null
     * here — the service layer fills it in by calling image-service when the
     * caller wants the resolved URL.
     */
    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .isActive(user.getIsActive())
                .role(user.getRole())
                .imageId(user.getImageId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Update existing User entity with data from UserUpdateRequest
     * Only updates non-null fields
     */
    public void updateEntity(User user, UserUpdateRequest request) {
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
    }
}
