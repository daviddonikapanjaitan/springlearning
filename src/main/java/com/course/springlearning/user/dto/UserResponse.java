package com.course.springlearning.user.dto;

import com.course.springlearning.user.entity.User;

import java.time.Instant;

// The password hash is intentionally never exposed
public record UserResponse(
        Long id,
        String email,
        String username,
        String fullName,
        String address,
        String gender,
        boolean enabled,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFullName(),
                user.getAddress(),
                user.getGender(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy()
        );
    }
}
