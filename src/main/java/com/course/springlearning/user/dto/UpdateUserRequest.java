package com.course.springlearning.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // Optional: null keeps the current password
        @Size(min = 8, max = 72)
        String password,

        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank @Size(max = 100)
        String fullName,

        @Size(max = 255)
        String address,

        @Size(max = 20)
        String gender,

        // Optional: null keeps the current value
        Boolean enabled
) {
}
