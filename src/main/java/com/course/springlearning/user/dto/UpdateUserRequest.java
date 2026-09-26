package com.course.springlearning.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Email and username are fixed after creation and cannot be updated
public record UpdateUserRequest(

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
