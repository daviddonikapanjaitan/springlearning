package com.course.springlearning.auth.controller;

import com.course.springlearning.auth.dto.LoginRequest;
import com.course.springlearning.auth.dto.LoginResponse;
import com.course.springlearning.auth.security.AuthenticatedUser;
import com.course.springlearning.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Public: returns a new JWT and revokes the user's previous token
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Needs the token: revokes and expires it
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        authService.logout((String) authentication.getCredentials(), user.username());
        return ResponseEntity.noContent().build();
    }
}
