package com.course.springlearning.auth.security;

import com.course.springlearning.auth.exception.InvalidTokenException;
import com.course.springlearning.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer &lt;token&gt;" and authenticates the request with {@link TokenService}.
 * A request without a valid token stays anonymous: protected endpoints then answer 401 through
 * {@link RestAuthenticationEntryPoint}, with the reason stored in {@link #AUTH_ERROR_ATTRIBUTE}.
 * <p>
 * Not a Spring bean on purpose: Spring Boot would also register a bean filter in the servlet chain,
 * outside Spring Security.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            if (token.isEmpty()) {
                throw new InvalidTokenException("Token is missing");
            }
            AuthenticatedUser user = tokenService.authenticate(token);
            // The token is kept as the credentials, logout needs it
            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, token, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
        }
        chain.doFilter(request, response);
    }
}
