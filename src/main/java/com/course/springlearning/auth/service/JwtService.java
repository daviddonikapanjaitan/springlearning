package com.course.springlearning.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and verifies HMAC-SHA signed JWTs.
 * Claims: {@code sub} = user id, {@code username}, {@code jti} (random, so every token is unique), {@code iat}, {@code exp}.
 */
@Component
public class JwtService {

    public static final String USERNAME_CLAIM = "username";

    private final SecretKey key;
    private final JwtParser parser;
    private final Duration expiration;

    public JwtService(@Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.expiration}") Duration expiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must be set");
        }
        // Throws WeakKeyException when the key is shorter than 256 bits
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret.trim()));
        // No clock skew: a token is rejected as soon as "exp" has passed
        this.parser = Jwts.parser().verifyWith(key).build();
        this.expiration = expiration;
    }

    public IssuedToken generate(Long userId, String username, Instant now) {
        // JWT dates have second precision, so expires_at in the table matches "exp" exactly
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expiration);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(USERNAME_CLAIM, username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Verifies the signature and expiry.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException when the signature is valid but "exp" has passed
     * @throws JwtException                        for any other invalid token
     */
    public Claims parse(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public Duration expiration() {
        return expiration;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
