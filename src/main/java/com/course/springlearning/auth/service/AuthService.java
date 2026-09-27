package com.course.springlearning.auth.service;

import com.course.springlearning.auth.dto.LoginRequest;
import com.course.springlearning.auth.dto.LoginResponse;
import com.course.springlearning.auth.exception.InvalidCredentialsException;
import com.course.springlearning.user.entity.User;
import com.course.springlearning.user.exception.UserDisabledException;
import com.course.springlearning.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Checked when the username does not exist, so both cases take as long as a real BCrypt check
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-unknown-users");
    }

    /**
     * Checks the username and password of an active user, revokes the user's old tokens
     * and returns a new token valid for {@code app.security.jwt.expiration} (1 hour).
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // SELECT ... FOR UPDATE: two logins of the same user run one after the other,
        // so the second one always revokes the token of the first one
        User user = userRepository.findWithLockByUsernameIgnoreCaseAndDeletedFalse(request.username().trim())
                .orElse(null);
        if (user == null) {
            passwordMatches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled()) {
            throw new UserDisabledException(user.getId());
        }

        JwtService.IssuedToken issued = tokenService.issue(user);
        long expiresIn = Math.max(0, Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
        return new LoginResponse(issued.token(), TOKEN_TYPE, expiresIn, issued.expiresAt());
    }

    /** Revokes and expires the token of the current request. */
    public void logout(String token, String actor) {
        tokenService.revoke(token, actor);
    }

    // BCrypt rejects passwords longer than 72 bytes, such a password can never match
    private boolean passwordMatches(String rawPassword, String hash) {
        try {
            return passwordEncoder.matches(rawPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
