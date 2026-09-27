package com.course.springlearning.auth.service;

import com.course.springlearning.auth.entity.Token;
import com.course.springlearning.auth.exception.InvalidTokenException;
import com.course.springlearning.auth.repository.TokenRepository;
import com.course.springlearning.auth.security.AuthenticatedUser;
import com.course.springlearning.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Issues, verifies and revokes JWT access tokens. The tokens table is the source of truth,
 * Redis ({@link TokenCache}) only caches the user of a valid token.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    // Stored in updated_by when a token is revoked without a known user
    static final String SYSTEM_ACTOR = "system";

    private final TokenRepository tokenRepository;
    private final JwtService jwtService;
    private final TokenCache tokenCache;

    public TokenService(TokenRepository tokenRepository, JwtService jwtService, TokenCache tokenCache) {
        this.tokenRepository = tokenRepository;
        this.jwtService = jwtService;
        this.tokenCache = tokenCache;
    }

    /**
     * Revokes every other token of the user, then creates, stores and caches a new one.
     * Must run in the caller's transaction, with the user row locked so two logins cannot both stay active.
     */
    @Transactional
    public JwtService.IssuedToken issue(User user) {
        String actor = user.getUsername();
        revokeAllForUser(user.getId(), actor);

        Instant now = now();
        JwtService.IssuedToken issued = jwtService.generate(user.getId(), user.getUsername(), now);

        Token token = new Token();
        token.setUser(user);
        token.setToken(issued.token());
        token.setExpiresAt(issued.expiresAt());
        token.setExpired(false);
        token.setRevoked(false);
        token.setCreatedAt(now);
        token.setCreatedBy(actor);
        token.setUpdatedAt(now);
        token.setUpdatedBy(actor);
        tokenRepository.saveAndFlush(token);

        tokenCache.putAfterCommit(issued.token(), new AuthenticatedUser(user.getId(), user.getUsername()),
                issued.expiresAt());
        return issued;
    }

    /**
     * Checks the signature and expiry of the JWT, then the tokens table (on every request, also on a Redis hit).
     * An expired token is revoked and expired in the table.
     *
     * @throws InvalidTokenException when the token cannot be used
     */
    public AuthenticatedUser authenticate(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (ExpiredJwtException e) {
            String actor = Optional.ofNullable(e.getClaims())
                    .map(expired -> expired.get(JwtService.USERNAME_CLAIM, String.class))
                    .orElse(SYSTEM_ACTOR);
            if (tokenRepository.revokeByToken(token, actor, now()) > 0) {
                log.info("Token of user {} has expired, marked as expired and revoked", actor);
            }
            tokenCache.evict(token);
            throw new InvalidTokenException("Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Token is invalid");
        }

        Instant now = now();
        Optional<AuthenticatedUser> cached = tokenCache.get(token);
        if (cached.isPresent()) {
            if (tokenRepository.isActive(token, now)) {
                return cached.get();
            }
        } else {
            Optional<AuthenticatedUser> user = tokenRepository.findActiveUserByToken(token, now);
            if (user.isPresent()) {
                tokenCache.put(token, user.get(), claims.getExpiration().toInstant());
                return user.get();
            }
        }

        // Revoked by logout or a newer login, or the user was disabled or deleted
        tokenCache.evict(token);
        throw new InvalidTokenException("Token has been revoked");
    }

    /** Logout: revokes and expires the token. */
    @Transactional
    public void revoke(String token, String actor) {
        tokenRepository.revokeByToken(token, actor, now());
        tokenCache.evictAfterCommit(List.of(token));
    }

    /** Revokes and expires every token of the user, e.g. on a new login, or when the user is disabled or deleted. */
    @Transactional
    public void revokeAllForUser(Long userId, String actor) {
        List<Token> tokens = tokenRepository.findAllNotRevokedByUserId(userId);
        if (tokens.isEmpty()) {
            return;
        }
        Instant now = now();
        for (Token token : tokens) {
            token.setRevoked(true);
            token.setExpired(true);
            token.setUpdatedAt(now);
            token.setUpdatedBy(actor);
        }
        tokenRepository.flush();
        tokenCache.evictAfterCommit(tokens.stream().map(Token::getToken).toList());
    }

    /** Revokes and expires every token whose expiry time has passed, returns how many were changed. */
    @Transactional
    public int revokeAllExpired() {
        return tokenRepository.revokeAllExpired(SYSTEM_ACTOR, now());
    }

    // PostgreSQL timestamptz stores microseconds, so truncate to keep responses identical to stored values
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
