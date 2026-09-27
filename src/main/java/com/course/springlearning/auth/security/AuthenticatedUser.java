package com.course.springlearning.auth.security;

/**
 * The user behind a valid JWT, available in controllers with {@code @AuthenticationPrincipal}.
 * Also the value cached in Redis for a token.
 */
public record AuthenticatedUser(
        Long userId,
        String username
) {
}
