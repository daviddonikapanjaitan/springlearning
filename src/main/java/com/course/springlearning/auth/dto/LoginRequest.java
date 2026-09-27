package com.course.springlearning.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50)
        String username,

        // BCrypt only uses the first 72 bytes of a password
        @NotBlank @Size(max = 72)
        String password
) {
}
