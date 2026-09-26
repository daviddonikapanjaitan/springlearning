package com.course.springlearning.order.dto;

// Kafka message published to the stream-order-users topic when an order is created
public record OrderCreatedMessage(
        Long orderId,
        Long userId,
        String invoiceNumber
) {
}
