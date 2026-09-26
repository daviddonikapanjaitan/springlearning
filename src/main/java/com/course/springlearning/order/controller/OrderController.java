package com.course.springlearning.order.controller;

import com.course.springlearning.order.dto.CompleteOrderRequest;
import com.course.springlearning.order.dto.CreateOrderRequest;
import com.course.springlearning.order.dto.OrderResponse;
import com.course.springlearning.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Who performs the action, stored in created_by / updated_by
    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "system";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Creates the order as PENDING and publishes it to the stream-order-users topic
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, resolveActor(actor)));
    }

    @PatchMapping("/{orderId}/complete")
    public OrderResponse complete(
            @PathVariable Long orderId,
            @Valid @RequestBody CompleteOrderRequest request,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return orderService.complete(orderId, request.userId(), resolveActor(actor));
    }

    private static String resolveActor(String actor) {
        return (actor == null || actor.isBlank()) ? DEFAULT_ACTOR : actor.trim();
    }
}
