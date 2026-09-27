package com.course.springlearning.auth.dto;

import java.time.Instant;

// Send the token as "Authorization: Bearer <accessToken>"
public record LoginResponse(
        String accessToken,
        String tokenType,
        // Seconds until the token expires
        long expiresIn,
        Instant expiresAt
) {
}
