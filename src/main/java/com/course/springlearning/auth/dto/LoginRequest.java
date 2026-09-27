package com.course.springlearning.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes of a password
        @NotBlank @Size(max = 72)
        String password
) {
}
