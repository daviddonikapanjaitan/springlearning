package com.course.springlearning.order.controller;

import com.course.springlearning.auth.security.AuthenticatedUser;
import com.course.springlearning.order.dto.CreateOrderRequest;
import com.course.springlearning.order.dto.OrderResponse;
import com.course.springlearning.order.service.OrderService;
import com.course.springlearning.user.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every endpoint needs "Authorization: Bearer <token>": the user comes from the token,
// and the token's username is stored in created_by / updated_by
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Creates the order as PENDING and publishes it to the stream-order-users topic
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(request, user.userId(), user.username()));
    }

    // The orders of the logged-in user, newest first
    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return orderService.findAll(user.userId(), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
    }

    @PatchMapping("/{orderId}/complete")
    public OrderResponse complete(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.complete(orderId, user.userId(), user.username());
    }
}
