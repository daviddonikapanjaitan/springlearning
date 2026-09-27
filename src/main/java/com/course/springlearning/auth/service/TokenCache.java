package com.course.springlearning.auth.service;

import com.course.springlearning.auth.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Redis cache of valid tokens, stored as JSON.
 * <ul>
 *   <li>{@code auth:tokens:{sha256 of the token}} holds the {@link AuthenticatedUser} of one token</li>
 * </ul>
 * An entry lives until the token expires. It is only a cache: every request still checks the tokens table.
 * Redis errors are logged and ignored so authentication keeps working from the database when Redis is down.
 */
@Component
public class TokenCache {

    private static final Logger log = LoggerFactory.getLogger(TokenCache.class);

    private static final String KEY_PREFIX = "auth:tokens:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public TokenCache(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    // A missing, unreadable or unparsable entry is treated as a cache miss
    public Optional<AuthenticatedUser> get(String token) {
        String key = key(token);
        try {
            return Optional.ofNullable(redis.opsForValue().get(key))
                    .map(json -> jsonMapper.readValue(json, AuthenticatedUser.class));
        } catch (RuntimeException e) {
            log.warn("Failed to read cache key {}, falling back to database", key, e);
            return Optional.empty();
        }
    }

    public void put(String token, AuthenticatedUser user, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        String key = key(token);
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(user), ttl);
        } catch (RuntimeException e) {
            log.warn("Failed to write cache key {}", key, e);
        }
    }

    /** Caches a newly issued token after the transaction commits, so a rolled back login is never cached. */
    public void putAfterCommit(String token, AuthenticatedUser user, Instant expiresAt) {
        afterCommit(() -> put(token, user, expiresAt));
    }

    public void evict(String token) {
        evict(List.of(token));
    }

    /** Removes revoked tokens after the transaction commits, so a concurrent request cannot re-cache them. */
    public void evictAfterCommit(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            afterCommit(() -> evict(tokens));
        }
    }

    private void evict(Collection<String> tokens) {
        try {
            redis.delete(tokens.stream().map(TokenCache::key).toList());
        } catch (RuntimeException e) {
            log.warn("Failed to evict {} cached token(s)", tokens.size(), e);
        }
    }

    // The key holds a hash, never the token itself
    private static String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
