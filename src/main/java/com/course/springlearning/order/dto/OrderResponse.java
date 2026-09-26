package com.course.springlearning.order.dto;

import com.course.springlearning.order.entity.Order;
import com.course.springlearning.order.entity.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        Long id,
        Long userId,
        String invoiceNumber,
        String itemName,
        long quantity,
        long itemPrice,
        long totalPrice,
        String orderDescription,
        boolean orderReceive,
        OrderStatus orderStatus,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                // Reading the id of a lazy relation does not load the user
                order.getUser().getId(),
                order.getInvoiceNumber(),
                order.getItemName(),
                order.getQuantity(),
                order.getItemPrice(),
                order.getTotalPrice(),
                order.getOrderDescription(),
                order.isOrderReceive(),
                order.getOrderStatus(),
                order.getCreatedAt(),
                order.getCreatedBy(),
                order.getUpdatedAt(),
                order.getUpdatedBy()
        );
    }
}
