package com.course.springlearning.order.service;

import com.course.springlearning.order.dto.OrderResponse;
import com.course.springlearning.user.dto.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Read-through Redis cache for the order list, stored as JSON.
 * <ul>
 *   <li>{@code orders:list:user:{userId|all}:page:{page}:size:{size}} holds one page of the list</li>
 *   <li>{@code orders:list:keys} is a set of all cached list keys, so they can be evicted without scanning Redis</li>
 * </ul>
 * Any order change (create, received by the Kafka listener, complete) evicts every cached page.
 * Redis errors are logged and ignored so the API keeps working from the database when Redis is down.
 */
@Component
public class OrderCache {

    private static final Logger log = LoggerFactory.getLogger(OrderCache.class);

    private static final String LIST_KEY_PREFIX = "orders:list:";
    private static final String LIST_KEYS_SET = "orders:list:keys";

    private static final TypeReference<PageResponse<OrderResponse>> PAGE_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public OrderCache(StringRedisTemplate redis, JsonMapper jsonMapper,
                      @Value("${app.cache.orders.ttl}") Duration ttl) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = ttl;
    }

    /** @param userId null caches the list of all orders */
    public PageResponse<OrderResponse> getPage(Long userId, int page, int size,
                                               Supplier<PageResponse<OrderResponse>> loader) {
        String key = LIST_KEY_PREFIX + "user:" + (userId == null ? "all" : userId) + ":page:" + page + ":size:" + size;

        PageResponse<OrderResponse> cached = read(key);
        if (cached != null) {
            return cached;
        }

        PageResponse<OrderResponse> result = loader.get();
        write(key, result);
        return result;
    }

    /**
     * Removes every cached list page (an order change can affect any page).
     * Runs after the transaction commits, so a concurrent read cannot re-cache the old data.
     */
    public void evictListsAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictListsNow();
                }
            });
        } else {
            evictListsNow();
        }
    }

    private void evictListsNow() {
        try {
            Set<String> listKeys = redis.opsForSet().members(LIST_KEYS_SET);
            if (listKeys != null && !listKeys.isEmpty()) {
                redis.delete(listKeys);
            }
            redis.delete(LIST_KEYS_SET);
        } catch (RuntimeException e) {
            log.warn("Failed to evict cached order lists", e);
        }
    }

    // A missing, unreadable or unparsable entry is treated as a cache miss
    private PageResponse<OrderResponse> read(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : jsonMapper.readValue(json, PAGE_TYPE);
        } catch (RuntimeException e) {
            log.warn("Failed to read cache key {}, falling back to database", key, e);
            return null;
        }
    }

    private void write(String key, PageResponse<OrderResponse> value) {
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
            redis.opsForSet().add(LIST_KEYS_SET, key);
            redis.expire(LIST_KEYS_SET, ttl);
        } catch (RuntimeException e) {
            log.warn("Failed to write cache key {}", key, e);
        }
    }
}
