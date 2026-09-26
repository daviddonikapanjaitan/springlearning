package com.course.springlearning.user;

import com.course.springlearning.user.dto.CreateUserRequest;
import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.dto.UpdateUserRequest;
import com.course.springlearning.user.dto.UserResponse;
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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim();

        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            throw new DuplicateUserException("Email '" + email + "' is already in use");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
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

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getActiveUser(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAllByDeletedFalse(pageable).map(UserResponse::from));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String actor) {
        User user = getActiveUser(id);
        String email = normalizeEmail(request.email());
        String username = request.username().trim();

        if (userRepository.existsByEmailAndDeletedFalseAndIdNot(email, id)) {
            throw new DuplicateUserException("Email '" + email + "' is already in use");
        }
        if (userRepository.existsByUsernameAndDeletedFalseAndIdNot(username, id)) {
            throw new DuplicateUserException("Username '" + username + "' is already in use");
        }

        user.setEmail(email);
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setAddress(trimToNull(request.address()));
        user.setGender(trimToNull(request.gender()));
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user.setUpdatedAt(now());
        user.setUpdatedBy(actor);

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    // Soft delete: the row stays in the table with is_deleted = true
    @Transactional
    public void delete(Long id, String actor) {
        User user = getActiveUser(id);
        user.setDeleted(true);
        user.setUpdatedAt(now());
        user.setUpdatedBy(actor);
        userRepository.saveAndFlush(user);
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
