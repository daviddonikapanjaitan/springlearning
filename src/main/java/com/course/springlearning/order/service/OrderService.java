package com.course.springlearning.order.service;

import com.course.springlearning.order.dto.CreateOrderRequest;
import com.course.springlearning.order.dto.OrderCreatedMessage;
import com.course.springlearning.order.dto.OrderResponse;
import com.course.springlearning.order.entity.Order;
import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.order.exception.InvalidOrderStatusException;
import com.course.springlearning.order.exception.OrderAccessDeniedException;
import com.course.springlearning.order.exception.OrderNotFoundException;
import com.course.springlearning.order.kafka.OrderEventPublisher;
import com.course.springlearning.order.repository.OrderRepository;
import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.entity.User;
import com.course.springlearning.user.exception.UserDisabledException;
import com.course.springlearning.user.exception.UserNotFoundException;
import com.course.springlearning.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderCache orderCache;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository,
                        OrderEventPublisher orderEventPublisher, OrderCache orderCache) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.orderCache = orderCache;
    }

    /**
     * Lists the orders of one user using the page, size and sort of the given pageable.
     * Not @Transactional: a cache hit should not open a database connection.
     */
    public PageResponse<OrderResponse> findAll(Long userId, Pageable pageable) {
        return orderCache.getPage(userId, pageable.getPageNumber(), pageable.getPageSize(),
                () -> PageResponse.from(orderRepository.findAllByUser_Id(userId, pageable).map(OrderResponse::from)));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long userId, String actor) {
        User user = getActiveEnabledUser(userId);

        Instant now = now();
        Order order = new Order();
        order.setUser(user);
        order.setInvoiceNumber(generateInvoiceNumber(now));
        order.setItemName(request.itemName().trim());
        order.setQuantity(request.quantity());
        order.setItemPrice(request.itemPrice());
        order.setTotalPrice(Math.multiplyExact(request.quantity(), request.itemPrice()));
        order.setOrderDescription(trimToNull(request.orderDescription()));
        order.setOrderReceive(false);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setCreatedBy(actor);
        order.setUpdatedAt(now);
        order.setUpdatedBy(actor);

        Order saved = orderRepository.saveAndFlush(order);
        orderEventPublisher.publishOrderCreatedAfterCommit(
                new OrderCreatedMessage(saved.getId(), user.getId(), saved.getInvoiceNumber()));
        orderCache.evictListsAfterCommit();
        return OrderResponse.from(saved);
    }

    /**
     * Called by the Kafka listener: PENDING -> IN_PROGRESS with order_receive = true.
     * Kafka can deliver a message more than once, so an order that is no longer PENDING is left untouched.
     */
    @Transactional
    public void markReceived(Long orderId, String actor) {
        Order order = orderRepository.findWithLockById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received order {} from Kafka but it does not exist, skipping", orderId);
            return;
        }
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {}, skipping duplicate message", orderId, order.getOrderStatus());
            return;
        }

        order.setOrderReceive(true);
        order.setOrderStatus(OrderStatus.IN_PROGRESS);
        order.setUpdatedAt(now());
        order.setUpdatedBy(actor);
        orderCache.evictListsAfterCommit();
        log.info("Order {} received, status changed to {}", orderId, OrderStatus.IN_PROGRESS);
    }

    /** IN_PROGRESS -> COMPLETED, only by the user who created the order while that user is enabled and not deleted. */
    @Transactional
    public OrderResponse complete(Long orderId, Long userId, String actor) {
        getActiveEnabledUser(userId);
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new OrderAccessDeniedException(orderId, userId);
        }
        switch (order.getOrderStatus()) {
            case PENDING -> throw new InvalidOrderStatusException(
                    "Order with id " + orderId + " has not been received yet and cannot be completed");
            case COMPLETED -> throw new InvalidOrderStatusException(
                    "Order with id " + orderId + " is already completed");
            case IN_PROGRESS -> {
                // allowed
            }
        }

        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setUpdatedAt(now());
        order.setUpdatedBy(actor);
        OrderResponse completed = OrderResponse.from(orderRepository.saveAndFlush(order));
        orderCache.evictListsAfterCommit();
        return completed;
    }

    // Only users with is_deleted = false and is_enabled = true may create or complete orders
    private User getActiveEnabledUser(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.isEnabled()) {
            throw new UserDisabledException(userId);
        }
        return user;
    }

    // e.g. INV-20260927-3F9A1C2B7D4E, uniqueness is also enforced by ux_orders_invoice_number
    private static String generateInvoiceNumber(Instant now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return "INV-" + INVOICE_DATE.format(now) + "-" + random;
    }

    // PostgreSQL timestamptz stores microseconds, so truncate to keep responses identical to stored values
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
