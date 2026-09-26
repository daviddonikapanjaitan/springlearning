package com.course.springlearning.order.entity;

// PENDING (created) -> IN_PROGRESS (received from Kafka) -> COMPLETED (completed by the ordering user)
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}
