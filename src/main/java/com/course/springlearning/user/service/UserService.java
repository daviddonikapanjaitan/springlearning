package com.course.springlearning.user.service;

import com.course.springlearning.auth.service.TokenService;
import com.course.springlearning.user.dto.CreateUserRequest;
import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.dto.UpdateUserRequest;
import com.course.springlearning.user.dto.UserResponse;
import com.course.springlearning.user.entity.User;
import com.course.springlearning.user.repository.UserRepository;
import com.course.springlearning.user.exception.DuplicateUserException;
import com.course.springlearning.user.exception.UserNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserCache userCache;
    private final TokenService tokenService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserCache userCache,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userCache = userCache;
        this.tokenService = tokenService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException("Email '" + email + "' is already in use");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUserException("Username '" + username + "' is already in use");
        }

        Instant now = now();
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setAddress(trimToNull(request.address()));
        user.setGender(trimToNull(request.gender()));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setDeleted(false);
        user.setCreatedAt(now);
        user.setCreatedBy(actor);
        user.setUpdatedAt(now);
        user.setUpdatedBy(actor);

        UserResponse created = UserResponse.from(userRepository.saveAndFlush(user));
        // A new user shifts the cached list pages
        userCache.evictListsAfterCommit();
        return created;
    }

    // Not @Transactional: a cache hit should not open a database connection
    public UserResponse findById(Long id) {
        return userCache.getUser(id, () -> UserResponse.from(getActiveUser(id)));
    }

    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return userCache.getPage(pageable.getPageNumber(), pageable.getPageSize(),
                () -> PageResponse.from(userRepository.findAllByDeletedFalse(pageable).map(UserResponse::from)));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String actor) {
        User user = getActiveUser(id);

        user.setFullName(request.fullName().trim());
        user.setAddress(trimToNull(request.address()));
        user.setGender(trimToNull(request.gender()));
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user.setUpdatedAt(now());
        user.setUpdatedBy(actor);

        UserResponse updated = UserResponse.from(userRepository.saveAndFlush(user));
        // A disabled user cannot use the API anymore
        if (!user.isEnabled()) {
            tokenService.revokeAllForUser(id, actor);
        }
        userCache.evictUserAfterCommit(id);
        return updated;
    }

    // Soft delete: the row stays in the table with is_deleted = true
    @Transactional
    public void delete(Long id, String actor) {
        User user = getActiveUser(id);
        user.setDeleted(true);
        user.setUpdatedAt(now());
        user.setUpdatedBy(actor);
        userRepository.saveAndFlush(user);
        // A deleted user cannot use the API anymore
        tokenService.revokeAllForUser(id, actor);
        userCache.evictUserAfterCommit(id);
    }

    private User getActiveUser(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    // PostgreSQL timestamptz stores microseconds, so truncate to keep responses identical to stored values
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
