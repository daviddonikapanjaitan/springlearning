package com.course.springlearning.user.service;

import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Read-through Redis cache for users, stored as JSON.
 * <ul>
 *   <li>{@code users:{id}} holds one user</li>
 *   <li>{@code users:list:page:{page}:size:{size}} holds one page of the list</li>
 *   <li>{@code users:list:keys} is a set of all cached list keys, so they can be evicted without scanning Redis</li>
 * </ul>
 * Redis errors are logged and ignored so the API keeps working from the database when Redis is down.
 */
@Component
public class UserCache {

    private static final Logger log = LoggerFactory.getLogger(UserCache.class);

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String USER_KEY_PREFIX = "users:";
    private static final String LIST_KEY_PREFIX = "users:list:";
    private static final String LIST_KEYS_SET = "users:list:keys";

    private static final TypeReference<PageResponse<UserResponse>> PAGE_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public UserCache(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    public UserResponse getUser(Long id, Supplier<UserResponse> loader) {
        String key = USER_KEY_PREFIX + id;
        return read(key, UserResponse.class).orElseGet(() -> {
            UserResponse user = loader.get();
            write(key, user);
            return user;
        });
    }

    public PageResponse<UserResponse> getPage(int page, int size, Supplier<PageResponse<UserResponse>> loader) {
        String key = LIST_KEY_PREFIX + "page:" + page + ":size:" + size;
        return read(key, PAGE_TYPE).orElseGet(() -> {
            PageResponse<UserResponse> result = loader.get();
            if (write(key, result)) {
                try {
                    redis.opsForSet().add(LIST_KEYS_SET, key);
                    redis.expire(LIST_KEYS_SET, TTL);
                } catch (RuntimeException e) {
                    log.warn("Failed to track cached list key {}", key, e);
                }
            }
            return result;
        });
    }

    /**
     * Removes the user and every cached list page (they may contain this user).
     * Runs after the transaction commits, so a concurrent read cannot re-cache the old data.
     */
    public void evictUserAfterCommit(Long id) {
        afterCommit(() -> {
            delete(USER_KEY_PREFIX + id);
            evictListsNow();
        });
    }

    /** Removes every cached list page, e.g. after a new user is created. */
    public void evictListsAfterCommit() {
        afterCommit(this::evictListsNow);
    }

    private void evictListsNow() {
        try {
            Set<String> listKeys = redis.opsForSet().members(LIST_KEYS_SET);
            if (listKeys != null && !listKeys.isEmpty()) {
                redis.delete(listKeys);
            }
            redis.delete(LIST_KEYS_SET);
        } catch (RuntimeException e) {
            log.warn("Failed to evict cached user lists", e);
        }
    }

    private void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to evict cache key {}", key, e);
        }
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        return read(key, json -> jsonMapper.readValue(json, type));
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        return read(key, json -> jsonMapper.readValue(json, type));
    }

    // A missing, unreadable or unparsable entry is treated as a cache miss
    private <T> Optional<T> read(String key, Function<String, T> parser) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key)).map(parser);
        } catch (RuntimeException e) {
            log.warn("Failed to read cache key {}, falling back to database", key, e);
            return Optional.empty();
        }
    }

    private boolean write(String key, Object value) {
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(value), TTL);
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to write cache key {}", key, e);
            return false;
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
