package com.course.springlearning.user.controller;

import com.course.springlearning.auth.security.AuthenticatedUser;
import com.course.springlearning.user.service.UserService;
import com.course.springlearning.user.dto.CreateUserRequest;
import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.dto.UpdateUserRequest;
import com.course.springlearning.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

// POST /api/users (registration) is public, every other endpoint needs "Authorization: Bearer <token>".
// The /me endpoints work on the user of the token, and store the token's username in updated_by
@RestController
@RequestMapping("/api/users")
public class UserController {

    // Public registration only: who performs the action, stored in created_by / updated_by
    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "system";
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        UserResponse created = userService.create(request, resolveActor(actor));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/me")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return userService.findAll(PageRequest.of(safePage, safeSize, Sort.by("id")));
    }

    @GetMapping("/me")
    public UserResponse findMe(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.findById(user.userId());
    }

    // Disabling yourself (enabled = false) revokes your tokens
    @PutMapping("/me")
    public UserResponse updateMe(
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return userService.update(user.userId(), request, user.username());
    }

    // Soft deletes yourself and revokes your tokens
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal AuthenticatedUser user) {
        userService.delete(user.userId(), user.username());
        return ResponseEntity.noContent().build();
    }

    private static String resolveActor(String actor) {
        return (actor == null || actor.isBlank()) ? DEFAULT_ACTOR : actor.trim();
    }
}
